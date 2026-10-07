// supabase/functions/smooth-endpoint/index.ts
//
// Generates the shared ("generic") photo for meals that don't have one yet
// and stores it at mensa-food/generic/<slug>.jpg. Runs on an hourly cron;
// `?limit=N` caps the number of images per run (default MAX_IMAGES or 8).
//
// Meals are grouped by their title *without allergen codes*: the raw title
// drifts day to day ("Senf-Kartoffeln (j)" → "Senf-Kartoffeln (3,j)"), and
// grouping on it generated a separate photo per code variant. Dishes on the
// plan in the next week go first, so today's menu isn't the one waiting.
//
// Expected env vars: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, OPENAI_API_KEY,
// MAX_IMAGES (optional per-run default).

// deno-lint-ignore-file no-explicit-any
import OpenAI from 'https://deno.land/x/openai@v4.24.0/mod.ts';
import { Image } from 'https://deno.land/x/imagescript@1.2.15/mod.ts';
import { log, sleep, supabase } from '../_shared/supabase.ts';
import { stripAllergenParens } from '../_shared/text.ts';
import { buildImagePrompt } from '../_shared/image_prompt.ts';
import { isNonDishEntry } from '../_shared/menu.ts';

const DELAY_PER_IMAGE_MS = 500; // ≈2 image requests / second
const UPCOMING_DAYS = 7;

interface MealRow {
  id: string;
  title: string;
  clean_title: string | null;
  title_en: string | null;
  course: string | null;
  sides: string[] | null;
}

interface Group {
  core: string;
  meals: MealRow[];
  upcoming: boolean;
}

async function retry<T>(fn: () => Promise<T>, retries = 2, delay = 2_000, tag = 'retry'): Promise<T> {
  try {
    return await fn();
  } catch (err) {
    log(`[${tag}] failed (${retries} left)`, err);
    if (retries === 0) throw err;
    await sleep(delay);
    return retry(fn, retries - 1, delay * 2, tag);
  }
}

// Same derivation as public.generic_image_slug(): JS \W without the /u flag
// is ASCII-only, so "ü" becomes "_" — keep it that way, paths depend on it.
function imageSlug(title: string): string {
  return title.replace(/\W+/g, '_').toLowerCase();
}

/** The row with the most structure (course + sides) drives the prompt. */
function representative(meals: MealRow[]): MealRow {
  return [...meals].sort((a, b) =>
    (b.course ? 2 : 0) + (b.sides?.length ? 1 : 0) - ((a.course ? 2 : 0) + (a.sides?.length ? 1 : 0))
  )[0];
}

async function loadGroups(limit: number): Promise<Group[]> {
  const { data, error } = await supabase
    .from('meals')
    .select('id, title, clean_title, title_en, course, sides')
    .is('image_path_generic', null);
  if (error) throw error;

  const start = new Date();
  const end = new Date(start);
  end.setUTCDate(start.getUTCDate() + UPCOMING_DAYS);
  const { data: upcomingRows, error: upErr } = await supabase
    .from('meal_dates')
    .select('meal_id')
    .gte('served_on', start.toISOString().slice(0, 10))
    .lte('served_on', end.toISOString().slice(0, 10))
    .is('deactivated_at', null);
  if (upErr) throw upErr;
  const upcomingIds = new Set((upcomingRows ?? []).map((r: any) => r.meal_id as string));

  const groups = new Map<string, Group>();
  for (const m of (data ?? []) as MealRow[]) {
    const core = (m.clean_title?.trim() || stripAllergenParens(m.title ?? '')).trim();
    if (!core || isNonDishEntry(core, '')) continue; // would fail every run and eat a slot
    let g = groups.get(core);
    if (!g) groups.set(core, (g = { core, meals: [], upcoming: false }));
    g.meals.push(m);
    g.upcoming ||= upcomingIds.has(m.id);
  }
  return [...groups.values()]
    .sort((a, b) => Number(b.upcoming) - Number(a.upcoming) || b.meals.length - a.meals.length)
    .slice(0, limit);
}

async function generate(openai: OpenAI, group: Group): Promise<string> {
  const rep = representative(group.meals);
  const prompt = buildImagePrompt({
    title: group.core,
    titleEn: rep.title_en,
    course: rep.course,
    sides: rep.sides,
  });
  if (!prompt) throw new Error('not a dish — skipped');

  const imgResp = await retry(
    () => openai.images.generate({ model: 'gpt-image-1', prompt, n: 1, size: '1024x1024', quality: 'low' } as any),
    2,
    2_000,
    `openai-${group.core}`,
  );
  const b64 = imgResp.data?.[0]?.b64_json;
  if (!b64) throw new Error('No image returned');
  const binStr = atob(b64);
  const png = new Uint8Array(binStr.length);
  for (let i = 0; i < binStr.length; i++) png[i] = binStr.charCodeAt(i);

  // 512px JPEG: plenty for the 88dp card and the detail hero, ~10× smaller than the PNG.
  const image = await Image.decode(png);
  image.resize(512, 512);
  const jpeg = await image.encodeJPEG(75);

  const path = `generic/${imageSlug(group.core)}.jpg`;
  const upRes = await supabase.storage.from('mensa-food').upload(path, jpeg, {
    contentType: 'image/jpeg',
    upsert: true,
  });
  if (upRes.error) throw upRes.error;

  const { error } = await supabase
    .from('meals')
    .update({ image_path_generic: path })
    .in('id', group.meals.map((m) => m.id));
  if (error) throw error;
  log(`BG: ✔ "${group.core}" [${rep.course ?? 'main?'}] (${(jpeg.length / 1024).toFixed(1)}kb)`);
  return path;
}

Deno.serve(async (req) => {
  log('── invocation ──');
  const url = new URL(req.url);
  const limit = Number(url.searchParams.get('limit') ?? Deno.env.get('MAX_IMAGES') ?? '8');
  if (!Number.isFinite(limit) || limit <= 0) {
    return new Response('`limit` must be a positive integer', { status: 400 });
  }
  const openaiKey = Deno.env.get('OPENAI_API_KEY');
  if (!openaiKey) {
    log('Missing OPENAI_API_KEY');
    return new Response('Missing env vars', { status: 500 });
  }

  let groups: Group[];
  try {
    groups = await loadGroups(limit);
  } catch (e) {
    log('Select error', e);
    return new Response('Database error', { status: 500 });
  }

  const bgPromise = (async () => {
    const openai = new OpenAI({ apiKey: openaiKey });
    log(`BG: starting, ${groups.length} titles`);
    let ok = 0;
    for (const group of groups) {
      try {
        await generate(openai, group);
        ok++;
      } catch (err) {
        log(`BG: ✖ "${group.core}"`, err);
      }
      await sleep(DELAY_PER_IMAGE_MS);
    }
    log(`BG: finished run (${ok}/${groups.length} ok)`);
  })();

  // Keep the runtime alive for the background work but answer now.
  // @ts-ignore: EdgeRuntime is provided by Supabase
  EdgeRuntime?.waitUntil?.(bgPromise);
  return new Response(
    JSON.stringify({ accepted: true, processing: groups.map((g) => g.core) }),
    { status: 202, headers: { 'Content-Type': 'application/json' } },
  );
});
