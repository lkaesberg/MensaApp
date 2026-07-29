// supabase/functions/mensa-menu-sync/index.ts
//
// Replaces the old HTML scraper. Consumes the official Studierendenwerk
// Göttingen mobile-app API:
//   GET https://app.studentenwerk-goettingen.de/api/mensaplanAll?token=1&datum=YYYY-MM-DD
//
// Returns XML with <speiseplan><mensa name="…"><speise id="…">…</speise></mensa></speiseplan>.
// Per-day call (the &datum= parameter is undocumented but verified live;
// without it the endpoint returns today only).
//
// Modes:
//   "today" → just today (every-30-min cron during opening hours)
//   "week"  → today..today+7  (daily cron)
//   "long"  → today..today+42 (weekly cron; the API typically dries up
//              somewhere around +30..40d, so over-asking is harmless)
//
// Same deactivation contract as the old scraper: the set of
// (canteen_id, served_on) pairs we successfully fetched becomes the
// "alive" set; rows in DB for those keys not in this run get
// deactivated_at = now(). Rows that reappear get re-activated.

// deno-lint-ignore-file no-explicit-any
import { DOMParser, Element } from 'https://deno.land/x/deno_dom/deno-dom-wasm.ts';

import {
  fetchWithRetry,
  getOrCreateCanteenId,
  log,
  mapWithConcurrency,
  supabase,
} from '../_shared/supabase.ts';
import { CANTEEN_SLUGS, CANTEEN_EXTERNAL_IDS, resolveKnownCanteen } from '../_shared/canteens.ts';
import {
  CODE_RE,
  parseEuroCents,
  splitAndSanitiseSides,
  stripAllergenParens,
} from '../_shared/text.ts';

const URL_BASE = 'https://app.studentenwerk-goettingen.de/api/mensaplanAll';
const SCRAPE_CONCURRENCY = 4;

// ───── XML parsing ─────────────────────────────────────────────────────────

interface Speise {
  externalId: number;
  date: string;          // ISO YYYY-MM-DD
  mensa: string;
  category: string;      // <preis>, e.g. "Menü Tasty"
  rawTitle: string;      // <essen>
  cleanTitle: string;    // stripped of allergen parens
  description: string;   // <essen2>
  titleEn: string | null;
  descriptionEn: string | null;
  sides: string[];
  sidesEn: string[];
  recipeName: string | null;
  fullText: string;      // = rawTitle (kept for legacy unique-key compat)
  icons: string[];
  allergens: string[];
  additives: string[];
  rating: number | null;
  // <mittag> integer — 0 = served whenever the canteen is open, >0 = lunch-only.
  // Persisted on `meal_dates` so the app can drive the Mittag/Nachmittag
  // split off a structured signal instead of free-text notes.
  mittag: number | null;
  // prices
  priceStudents: string | null;
  priceEmployees: string | null;
  priceGuests: string | null;
}

function txt(el: Element | null, tag: string): string {
  if (!el) return '';
  const child = el.querySelector(tag);
  return (child?.textContent ?? '').trim();
}

function bool(el: Element, tag: string): boolean {
  return txt(el, tag).length > 0;
}

// API booleans → icon slugs the Compose UI already understands.
// Slugs without an icon in the app render as "?" pills — harmless
// forward-compat for new flags.
function iconsFor(speise: Element): string[] {
  const out = new Set<string>();
  if (bool(speise, 'vegan')) out.add('vegan');
  if (bool(speise, 'vegetarisch')) out.add('vegetarisch');

  const title = txt(speise, 'essen');
  if (bool(speise, 'schweinefleisch')) {
    out.add('fleisch');
    if (/strohschwein/i.test(title)) out.add('strohschwein');
  }
  if (bool(speise, 'rind')) {
    out.add('fleisch');
    if (/leinetaler/i.test(title)) out.add('leinetalerrind');
  }
  if (bool(speise, 'gefluegel') || bool(speise, 'lamm') || bool(speise, 'wild')) out.add('fleisch');
  if (bool(speise, 'fisch')) out.add('fisch');
  if (bool(speise, 'bio')) out.add('bio');
  if (bool(speise, 'regional')) out.add('regional');

  // Pass these through verbatim — UI will fall back to the short letter
  // until icons are added in Tokens.kt.
  if (bool(speise, 'msc')) out.add('msc');
  if (bool(speise, 'halal')) out.add('halal');
  if (bool(speise, 'laktosefrei')) out.add('lakto');
  if (bool(speise, 'vital')) out.add('vital');
  if (bool(speise, 'knoblauch')) out.add('knoblauch');
  if (bool(speise, 'hausgemacht')) out.add('hausgemacht');
  if (bool(speise, 'aktion')) out.add('aktion');
  if (bool(speise, 'alkohol')) out.add('alkohol');

  // NDS-Menü tag is in <zusatzstoffe>, not its own boolean.
  const z = txt(speise, 'zusatzstoffe');
  if (/(^|,)\s*NDS\s*(,|$)/.test(z)) out.add('nds');

  return [...out];
}

// Splits <zusatzstoffe> into structured allergen + additive arrays.
// Word tags ("vgn", "fllo", "konv", "NDS", "vegt", "Flei") are dropped
// — they're already encoded in the dedicated boolean flags.
function splitCodes(zusatz: string): { allergens: string[]; additives: string[] } {
  const allergens: string[] = [];
  const additives: string[] = [];
  for (const partRaw of zusatz.split(',')) {
    const p = partRaw.trim();
    if (!p || !CODE_RE.test(p)) continue;
    if (/^[0-9]/.test(p)) additives.push(p);
    else allergens.push(p.toLowerCase());
  }
  return {
    allergens: [...new Set(allergens)],
    additives: [...new Set(additives)],
  };
}

function parseSpeise(speise: Element, mensaName: string): Speise | null {
  const idAttr = speise.getAttribute('id');
  const externalId = idAttr ? Number(idAttr) : NaN;
  if (!Number.isFinite(externalId)) return null;
  const date = txt(speise, 'date');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return null;

  const rawTitle = txt(speise, 'essen');
  // The API returns placeholder <speise> entries for empty "Last Minute"
  // category slots (everything blank). They dedup to the same empty-title
  // meals row across canteens, which then collides on the legacy
  // (meal_id, canteen_id, served_on) UNIQUE — and they're not menu items
  // anyway. Skip them.
  if (!rawTitle.trim()) return null;
  const description = txt(speise, 'essen2');
  const titleEn = txt(speise, 'essen_eng') || null;
  const descriptionEn = txt(speise, 'essen2_eng') || null;
  const cleanTitle = stripAllergenParens(rawTitle);
  const category = txt(speise, 'preis');
  const recipeName = txt(speise, 'rezeptname') || null;
  const sterneRaw = parseFloat(txt(speise, 'sterne') || 'NaN');
  const rating = Number.isFinite(sterneRaw) && sterneRaw > 0 ? sterneRaw : null;

  const { allergens, additives } = splitCodes(txt(speise, 'zusatzstoffe'));

  const priceStudents = txt(speise, 'preis_stu') || null;
  const priceEmployees = txt(speise, 'preis_mit') || null;
  const priceGuests = txt(speise, 'preis_gas') || null;
  const mittagRaw = parseInt(txt(speise, 'mittag'), 10);
  const mittag = Number.isFinite(mittagRaw) ? mittagRaw : null;

  return {
    externalId,
    date,
    mensa: mensaName,
    category,
    rawTitle,
    cleanTitle,
    description,
    titleEn,
    descriptionEn,
    sides: splitAndSanitiseSides(description),
    sidesEn: splitAndSanitiseSides(descriptionEn ?? ''),
    recipeName,
    fullText: rawTitle, // keep raw with codes as the legacy unique key
    icons: iconsFor(speise),
    allergens,
    additives,
    rating,
    mittag,
    priceStudents,
    priceEmployees,
    priceGuests,
  };
}

function parseDayXml(xml: string): Speise[] {
  // deno-dom's HTML parser drops <![CDATA[...]]> sections (treats them as
  // comments), which would leave every <date>/<essen>/etc. textContent
  // empty. The upstream wraps every text node in CDATA, so strip the
  // markers up front and let HTML parsing read the inner text directly.
  // CDATA bodies in this API are plain UTF-8 (no <, >, &) so the regex is
  // safe — HTML entities like `&auml;` survive the strip and get decoded
  // by the parser as expected.
  const stripped = xml.replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1');
  const doc = new DOMParser().parseFromString(stripped, 'text/html');
  if (!doc) return [];
  const out: Speise[] = [];
  for (const mensaNode of doc.querySelectorAll('mensa')) {
    const mensa = mensaNode as Element;
    const name = mensa.getAttribute('name');
    if (!name) continue;
    for (const speiseNode of mensa.querySelectorAll('speise')) {
      const speise = parseSpeise(speiseNode as Element, name);
      if (speise) out.push(speise);
    }
  }
  return out;
}

// ───── DB ──────────────────────────────────────────────────────────────────

interface UpsertResult {
  // keys "<canteen_id>|<category>" of meal_dates we successfully wrote, per day
  seenByDay: Map<string, Set<string>>;
  // canteen_ids we actually resolved this run — deactivation is scoped to
  // these so a canteen we skipped doesn't get its whole plan soft-deleted.
  resolvedCanteenIds: Set<string>;
  // <mensa name> values we didn't recognise. Surfaced in the HTTP response
  // and the logs so a naming change upstream is visible instead of silently
  // forking a new canteen.
  unknownCanteens: string[];
  ok: number;
  fail: number;
}

async function upsertDay(speisen: Speise[]): Promise<UpsertResult> {
  const seenByDay = new Map<string, Set<string>>();
  const resolvedCanteenIds = new Set<string>();
  const unknownCanteens: string[] = [];
  let ok = 0;
  let fail = 0;
  if (speisen.length === 0) {
    return { seenByDay, resolvedCanteenIds, unknownCanteens, ok, fail };
  }

  try {
    // 1. Resolve canteen IDs once per distinct name. The existing cache
    //    means subsequent days hit memory; first run for each canteen
    //    upserts the row. We also backfill slug/external_id for the four
    //    mensas in case the migration didn't catch them (auto-created
    //    rows can be name-only).
    //    Strict resolution: this endpoint serves a known, fixed set of
    //    mensas, so an unrecognised <mensa name> is a naming change upstream,
    //    not a new venue. Report it and skip — auto-creating is what forked
    //    "CGIN" off "CGiN" on 2026-07-24. Fixing a future one is a line in
    //    CANTEEN_NAME_ALIASES.
    const canteenNames = [...new Set(speisen.map((s) => s.mensa))];
    const canteenIdByName = new Map<string, string>();
    for (const name of canteenNames) {
      const id = await getOrCreateCanteenId(name, { allowCreate: false });
      if (!id) {
        unknownCanteens.push(name);
        log(`⚠ unknown canteen name "${name}" — skipping its dishes`);
        continue;
      }
      canteenIdByName.set(name, id);
      resolvedCanteenIds.add(id);
      const canonical = resolveKnownCanteen(name);
      if (canonical && CANTEEN_SLUGS[canonical]) {
        await supabase
          .from('canteens')
          .update({
            slug: CANTEEN_SLUGS[canonical],
            external_id: CANTEEN_EXTERNAL_IDS[canonical] ?? null,
          })
          .eq('id', id)
          .is('slug', null);
      }
    }

    // 2. Bulk upsert meals. Dedupe by external_id — `<speise id>` is the
    //    stable upstream key, so the same dish across multiple days
    //    collapses to one row. Postgres' ON CONFLICT also requires unique
    //    keys within the statement, and we made external_id the conflict
    //    target in the 2026-05-08 migration (full_text drifts day-to-day
    //    as allergen codes change, so it's no longer a reliable dedup key).
    const mealRowByExternalId = new Map<number, Record<string, unknown>>();
    for (const s of speisen) {
      mealRowByExternalId.set(s.externalId, {
        title: s.rawTitle,
        full_text: s.fullText,
        clean_title: s.cleanTitle,
        description: s.description,
        sides: s.sides,
        icons: s.icons,
        allergens: s.allergens,
        additives: s.additives,
        external_id: s.externalId,
        title_en: s.titleEn,
        description_en: s.descriptionEn,
        sides_en: s.sidesEn,
        rating_avg: s.rating,
        recipe_name: s.recipeName,
      });
    }
    const mealPayloads = [...mealRowByExternalId.values()];
    const { data: upsertedMeals, error: mealErr } = await supabase
      .from('meals')
      .upsert(mealPayloads, { onConflict: 'external_id' })
      .select('id, external_id');
    if (mealErr) throw mealErr;
    const mealIdByExternalId = new Map<number, string>();
    for (const m of (upsertedMeals ?? []) as Array<{ id: string; external_id: number }>) {
      mealIdByExternalId.set(m.external_id, m.id);
    }

    // 3. Bulk upsert meal_dates. Dedupe by (canteen_id, served_on, category)
    //    only — that's the upsert conflict target. The legacy
    //    (meal_id, canteen_id, served_on) UNIQUE was dropped in the
    //    2026-05-10 migration, so the same dish can now legitimately appear
    //    under two categories on one day (e.g. "Menü 1" + "Salatbuffet").
    const dateRowByPrimary = new Map<string, Record<string, unknown>>();
    for (const s of speisen) {
      const canteenId = canteenIdByName.get(s.mensa);
      const mealId = mealIdByExternalId.get(s.externalId);
      if (!canteenId || !mealId) {
        fail++;
        continue;
      }
      const primaryKey = `${canteenId}|${s.date}|${s.category}`;
      dateRowByPrimary.set(primaryKey, {
        meal_id: mealId,
        canteen_id: canteenId,
        served_on: s.date,
        category: s.category,
        deactivated_at: null,
        mittag: s.mittag,
        price_students: s.priceStudents,
        price_employees: s.priceEmployees,
        price_guests: s.priceGuests,
        price_students_cents: parseEuroCents(s.priceStudents),
        price_employees_cents: parseEuroCents(s.priceEmployees),
        price_guests_cents: parseEuroCents(s.priceGuests),
      });
      let set = seenByDay.get(s.date);
      if (!set) seenByDay.set(s.date, (set = new Set()));
      set.add(`${canteenId}|${s.category}`);
    }
    const datePayloads = [...dateRowByPrimary.values()];
    if (datePayloads.length > 0) {
      const { error: dateErr } = await supabase
        .from('meal_dates')
        .upsert(datePayloads, {
          onConflict: 'canteen_id,served_on,category',
          ignoreDuplicates: false,
        });
      if (dateErr) throw dateErr;
    }
    ok = datePayloads.length;
  } catch (e) {
    log('bulk upsert error:', e);
    fail += speisen.length - ok;
  }

  return { seenByDay, resolvedCanteenIds, unknownCanteens, ok, fail };
}

async function deactivateStale(
  successfulDays: string[],
  seenByDay: Map<string, Set<string>>,
  resolvedCanteenIds: Set<string>,
): Promise<number> {
  if (successfulDays.length === 0 || resolvedCanteenIds.size === 0) return 0;
  const { data, error } = await supabase
    .from('meal_dates')
    .select('id, canteen_id, served_on, category, deactivated_at')
    .in('served_on', successfulDays)
    // Scope to canteens this run actually resolved. Without it, a canteen we
    // skipped (unknown name) or that upstream omitted would have its entire
    // plan soft-deleted just for being absent from `seenByDay`.
    .in('canteen_id', [...resolvedCanteenIds]);
  if (error) {
    log('deactivate: select error', error);
    return 0;
  }
  const stale: string[] = [];
  for (const row of data ?? []) {
    if (row.deactivated_at) continue;
    const seen = seenByDay.get(row.served_on);
    const key = `${row.canteen_id}|${row.category}`;
    if (!seen || !seen.has(key)) stale.push(row.id);
  }
  if (stale.length === 0) return 0;
  const nowISO = new Date().toISOString();
  let updated = 0;
  for (let i = 0; i < stale.length; i += 200) {
    const chunk = stale.slice(i, i + 200);
    const { error: upErr, count } = await supabase
      .from('meal_dates')
      .update({ deactivated_at: nowISO }, { count: 'exact' })
      .in('id', chunk);
    if (upErr) {
      log('deactivate: update error', upErr);
      continue;
    }
    updated += count ?? chunk.length;
  }
  return updated;
}

// ───── HTTP entry ──────────────────────────────────────────────────────────

interface DayResult {
  date: string;
  ok: boolean;
  speisen: Speise[];
}

function isoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

async function fetchDay(date: string): Promise<DayResult> {
  const url = `${URL_BASE}?token=1&datum=${date}`;
  const res = await fetchWithRetry(url, 'application/xml');
  if (!res || !res.ok) return { date, ok: false, speisen: [] };
  const xml = await res.text();
  const speisen = parseDayXml(xml);
  log(`✓ ${date} — ${speisen.length} dishes`);
  return { date, ok: true, speisen };
}

Deno.serve(async (req) => {
  log('── mensa-menu-sync ──');
  let mode: 'today' | 'week' | 'long' = 'today';
  try {
    const body = await req.text();
    if (body.trim()) {
      const parsed = JSON.parse(body) as { mode?: string };
      if (parsed.mode === 'week' || parsed.mode === 'long' || parsed.mode === 'today') {
        mode = parsed.mode;
      }
    }
  } catch {
    // Body optional; default to today.
  }

  const days = mode === 'today' ? 1 : mode === 'week' ? 8 : 43;
  const start = new Date();
  start.setUTCHours(0, 0, 0, 0);
  const dates: string[] = [];
  for (let i = 0; i < days; i++) {
    const d = new Date(start);
    d.setUTCDate(start.getUTCDate() + i);
    dates.push(isoDate(d));
  }

  log(`mode=${mode}, scanning ${dates.length} days`);
  const results = await mapWithConcurrency(dates, SCRAPE_CONCURRENCY, fetchDay);

  // Aggregate.
  const allSpeisen: Speise[] = [];
  const successfulDays: string[] = [];
  let totalOk = 0;
  let totalFail = 0;
  for (const r of results) {
    if (!r.ok) continue;
    successfulDays.push(r.date);
    allSpeisen.push(...r.speisen);
  }
  const upsert = await upsertDay(allSpeisen);
  totalOk = upsert.ok;
  totalFail = upsert.fail;
  const deactivated = await deactivateStale(
    successfulDays,
    upsert.seenByDay,
    upsert.resolvedCanteenIds,
  );

  if (upsert.unknownCanteens.length > 0) {
    log(`⚠ unknown canteen names this run: ${upsert.unknownCanteens.join(', ')}`);
  }
  log(`done. ok=${totalOk} fail=${totalFail} deactivated=${deactivated} days=${successfulDays.length}/${dates.length}`);
  return new Response(
    JSON.stringify({
      mode,
      days_scanned: dates.length,
      days_ok: successfulDays.length,
      upsert_ok: totalOk,
      upsert_fail: totalFail,
      deactivated,
      unknown_canteens: upsert.unknownCanteens,
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );
});
