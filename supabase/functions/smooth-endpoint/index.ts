// supabase/functions/smooth-endpoint/index.ts
//
// Generates the shared ("generic") photo for meals that don't have one yet
// and stores it at mensa-food/generic/<slug>.jpg. Runs on an hourly cron;
// `?limit=N` caps the number of images per run (default MAX_IMAGES or 8).
//
// It also photographs every side and dessert on the coming week's plans —
// each option of a side/salad/dessert counter and the real sides of the
// mains (no sauces or garnish) — at mensa-food/sides/<slug>.jpg for the app's
// sides and desserts screens. `?side_limit=N` caps those per run (default
// MAX_SIDE_IMAGES or 8). They have their own folder because generic/<slug>
// may still hold an old tray-style photo.
//
// Meals are grouped by their title *without allergen codes*: the raw title
// drifts day to day ("Senf-Kartoffeln (j)" → "Senf-Kartoffeln (3,j)"), and
// grouping on it generated a separate photo per code variant. Dishes on the
// plan in the next week go first, so today's menu isn't the one waiting.
//
// Expected env vars: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, OPENAI_API_KEY,
// MAX_IMAGES, MAX_SIDE_IMAGES (optional per-run defaults).

// deno-lint-ignore-file no-explicit-any
import OpenAI from 'https://deno.land/x/openai@v4.24.0/mod.ts';
import { Image } from 'https://deno.land/x/imagescript@1.2.15/mod.ts';
import { log, sleep, supabase } from '../_shared/supabase.ts';
import { stripAllergenParens } from '../_shared/text.ts';
import { buildImagePrompt } from '../_shared/image_prompt.ts';
import { isNonDishEntry, PhotoItem, photoItemsOf, sideImageFile } from '../_shared/menu.ts';

const DELAY_PER_IMAGE_MS = 500; // ≈2 image requests / second
const UPCOMING_DAYS = 7;
const BUCKET = 'mensa-food';
const SIDE_FOLDER = 'sides';

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

function upcomingRange(): [string, string] {
  const start = new Date();
  const end = new Date(start);
  end.setUTCDate(start.getUTCDate() + UPCOMING_DAYS);
  return [start.toISOString().slice(0, 10), end.toISOString().slice(0, 10)];
}

async function loadGroups(limit: number): Promise<Group[]> {
  const { data, error } = await supabase
    .from('meals')
    .select('id, title, clean_title, title_en, course, sides')
    .is('image_path_generic', null);
  if (error) throw error;

  const [from, to] = upcomingRange();
  const { data: upcomingRows, error: upErr } = await supabase
    .from('meal_dates')
    .select('meal_id')
    .gte('served_on', from)
    .lte('served_on', to)
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

/** Generate, shrink and upload one photo; returns its size in bytes. */
async function renderAndUpload(openai: OpenAI, prompt: string, path: string, tag: string): Promise<number> {
  const imgResp = await retry(
    () => openai.images.generate({ model: 'gpt-image-1', prompt, n: 1, size: '1024x1024', quality: 'low' } as any),
    2,
    2_000,
    `openai-${tag}`,
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

  const upRes = await supabase.storage.from(BUCKET).upload(path, jpeg, {
    contentType: 'image/jpeg',
    upsert: true,
  });
  if (upRes.error) throw upRes.error;
  return jpeg.length;
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

  const path = `generic/${imageSlug(group.core)}.jpg`;
  const size = await renderAndUpload(openai, prompt, path, group.core);

  const { error } = await supabase
    .from('meals')
    .update({ image_path_generic: path })
    .in('id', group.meals.map((m) => m.id));
  if (error) throw error;
  log(`BG: ✔ "${group.core}" [${rep.course ?? 'main?'}] (${(size / 1024).toFixed(1)}kb)`);
  return path;
}

async function existingSidePhotos(): Promise<Set<string>> {
  const names = new Set<string>();
  for (let offset = 0; ; offset += 1000) {
    const { data, error } = await supabase.storage.from(BUCKET).list(SIDE_FOLDER, { limit: 1000, offset });
    if (error) throw error;
    for (const o of data ?? []) names.add(o.name);
    if (!data || data.length < 1000) return names;
  }
}

/** Sides and desserts on the coming week's plans without a photo, soonest and most frequent first. */
async function loadMissingSides(limit: number): Promise<PhotoItem[]> {
  if (limit <= 0) return [];
  const [from, to] = upcomingRange();
  const { data, error } = await supabase
    .from('meal_dates')
    .select('served_on, meals(title, clean_title, course, sides, alternatives)')
    .gte('served_on', from)
    .lte('served_on', to)
    .is('deactivated_at', null);
  if (error) throw error;

  const seen = new Map<string, { item: PhotoItem; first: string; count: number }>();
  for (const row of (data ?? []) as any[]) {
    if (!row.meals) continue;
    for (const item of photoItemsOf(row.meals)) {
      const file = sideImageFile(item.name);
      const s = seen.get(file);
      if (!s) seen.set(file, { item, first: row.served_on, count: 1 });
      else {
        s.count++;
        if (row.served_on < s.first) s.first = row.served_on;
      }
    }
  }
  const existing = await existingSidePhotos();
  return [...seen.entries()]
    .filter(([file]) => !existing.has(file))
    .map(([, s]) => s)
    .sort((a, b) => a.first.localeCompare(b.first) || b.count - a.count)
    .slice(0, limit)
    .map((s) => s.item);
}

async function generateSide(openai: OpenAI, item: PhotoItem): Promise<void> {
  const prompt = buildImagePrompt({ title: item.name, course: item.course });
  if (!prompt) throw new Error('not a dish — skipped');
  const size = await renderAndUpload(openai, prompt, `${SIDE_FOLDER}/${sideImageFile(item.name)}`, item.name);
  log(`BG: ✔ ${item.course} "${item.name}" (${(size / 1024).toFixed(1)}kb)`);
}

Deno.serve(async (req) => {
  log('── invocation ──');
  const url = new URL(req.url);
  const limit = Number(url.searchParams.get('limit') ?? Deno.env.get('MAX_IMAGES') ?? '8');
  if (!Number.isFinite(limit) || limit <= 0) {
    return new Response('`limit` must be a positive integer', { status: 400 });
  }
  const sideLimit = Number(url.searchParams.get('side_limit') ?? Deno.env.get('MAX_SIDE_IMAGES') ?? '8');
  if (!Number.isFinite(sideLimit) || sideLimit < 0) {
    return new Response('`side_limit` must be a non-negative integer', { status: 400 });
  }
  const openaiKey = Deno.env.get('OPENAI_API_KEY');
  if (!openaiKey) {
    log('Missing OPENAI_API_KEY');
    return new Response('Missing env vars', { status: 500 });
  }

  let groups: Group[];
  let sides: PhotoItem[];
  try {
    groups = await loadGroups(limit);
    sides = await loadMissingSides(sideLimit);
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
    let sidesOk = 0;
    for (const item of sides) {
      try {
        await generateSide(openai, item);
        sidesOk++;
      } catch (err) {
        log(`BG: ✖ ${item.course} "${item.name}"`, err);
      }
      await sleep(DELAY_PER_IMAGE_MS);
    }
    log(`BG: finished run (${ok}/${groups.length} meals, ${sidesOk}/${sides.length} sides ok)`);
  })();

  // Keep the runtime alive for the background work but answer now.
  // @ts-ignore: EdgeRuntime is provided by Supabase
  EdgeRuntime?.waitUntil?.(bgPromise);
  return new Response(
    JSON.stringify({ accepted: true, processing: groups.map((g) => g.core), sides: sides.map((s) => s.name) }),
    { status: 202, headers: { 'Content-Type': 'application/json' } },
  );
});
