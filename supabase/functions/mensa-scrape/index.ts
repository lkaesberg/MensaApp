// supabase/functions/mensa-scrape/index.ts
//
// Scrapes daily menu plans + price lists from Studierendenwerk Göttingen
// and upserts them into Supabase. Source of truth is the upstream HTML;
// when a successfully fetched day no longer lists a meal, that meal_date
// row is soft-deactivated (deactivated_at = now). When it reappears, we
// re-activate (deactivated_at = null).
//
// Upstream "API" (HTML, no JSON):
//   Daily plan, all canteens for one day:
//     https://www.studierendenwerk-goettingen.de/fileadmin/templates/php/
//     mensaspeiseplan/cached/de/YYYY-MM-DD/alle.html        (200 = data, 404 = no plan)
//   Per-canteen daily plan (lowercase, spaces→underscores), e.g.:
//     .../cached/de/YYYY-MM-DD/zentralmensa.html
//     .../cached/de/YYYY-MM-DD/mensa_am_turm.html
//     .../cached/de/YYYY-MM-DD/cgin.html
//     .../cached/de/YYYY-MM-DD/bistro_hawk.html
//   English variant: swap /de/ for /en/.
//   Per-canteen page (with prices, news, opening hours):
//     /campusgastronomie/mensen/<slug>
//
// Daily HTML structure (one <table class="sp_tab"> per canteen):
//   <th><strong>{canteen}</strong><div class="sp_date">DD.MM.YYYY</div></th>
//   <tr> per offer:
//     <td class="sp_typ">{category}                      e.g. "Vegan", "Menü", "CampusCurry"
//     <td class="sp_bez">
//       <strong>{title} ({allergens/additives})</strong> e.g. "Spätzle (a.1,a,c)"
//       <br/>{side1} ({codes}), {side2}, …               sides, comma-separated
//       <i class="smaller">({meal period})</i>           e.g. "(Mittagsangebot)"
//     <td class="sp_hin"><img src=".../{icon}.png"/>…    diet/origin icons
//
// Code reference (EU/German Mensa standard, unchanged for years):
//   Allergens (a–n; many have sub-types like a.1):
//     a = glutenhaltige Getreide  (a.1 Weizen, a.2 Roggen, a.3 Gerste,
//                                  a.4 Hafer, a.5 Dinkel, a.6 Kamut)
//     b = Krebstiere   c = Eier   d = Fisch   e = Erdnüsse   f = Soja
//     g = Milch         h = Schalenfrüchte   (h.1–h.8)
//     i = Sellerie   j = Senf   k = Sesam   l = Sulfite   m = Lupine   n = Weichtiere
//   Additives (1–11):
//     1 Farbstoff       2 Konservierungsstoff  3 Antioxidationsmittel
//     4 Geschmacksverstärker  5 Geschwefelt   6 Geschwärzt
//     7 Gewachst        8 Phosphat            9 Süßungsmittel
//    10 Phenylalaninquelle  11 Koffeinhaltig
//   Diet/origin icons (filename without extension):
//     vegan, vegetarisch, fleisch, fisch, strohschwein, leinetalerrind, nds

// deno-lint-ignore-file no-explicit-any
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.39.6';
import { DOMParser, Element } from 'https://deno.land/x/deno_dom/deno-dom-wasm.ts';

const SUPABASE_URL = Deno.env.get('SUPABASE_URL');
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
  throw new Error('Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY');
}
const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
  auth: { persistSession: false },
});

const log = (msg: string, ...args: any[]) =>
  console.log(`${new Date().toISOString()}  ${msg}`, ...args);
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

// ───── fetch ────────────────────────────────────────────────────────────────

const ORIGIN = 'https://www.studierendenwerk-goettingen.de';
const MENU_BASE = `${ORIGIN}/fileadmin/templates/php/mensaspeiseplan/cached/de`;
const USER_AGENT = 'MensaApp-Scraper/1.0 (+https://github.com/larskaesberg)';
const FETCH_TIMEOUT_MS = 15_000;
const FETCH_ATTEMPTS = 3;
const SCRAPE_CONCURRENCY = 4;

// Returns null on definitive failure. 404 returned as-is so callers can
// distinguish "no plan" from "transient error".
async function fetchWithRetry(url: string): Promise<Response | null> {
  let lastErr: unknown;
  for (let i = 0; i < FETCH_ATTEMPTS; i++) {
    try {
      const ctrl = new AbortController();
      const timer = setTimeout(() => ctrl.abort(), FETCH_TIMEOUT_MS);
      const res = await fetch(url, {
        headers: { 'User-Agent': USER_AGENT, Accept: 'text/html' },
        signal: ctrl.signal,
      });
      clearTimeout(timer);
      if (res.status === 404) return res;
      // retry on 5xx and 429; surface other 4xx (terminal)
      if (res.ok || (res.status >= 400 && res.status < 500 && res.status !== 429)) {
        return res;
      }
      lastErr = new Error(`HTTP ${res.status}`);
    } catch (e) {
      lastErr = e;
    }
    if (i < FETCH_ATTEMPTS - 1) {
      const backoff = 500 * 2 ** i;
      log(`fetch retry ${i + 1}/${FETCH_ATTEMPTS - 1} in ${backoff}ms (${url}): ${lastErr}`);
      await sleep(backoff);
    }
  }
  log(`fetch giving up on ${url}: ${lastErr}`);
  return null;
}

async function mapWithConcurrency<T, U>(
  items: T[],
  n: number,
  fn: (t: T) => Promise<U>,
): Promise<U[]> {
  const out: U[] = new Array(items.length);
  let cursor = 0;
  await Promise.all(
    Array.from({ length: Math.min(n, items.length) }, async () => {
      while (true) {
        const idx = cursor++;
        if (idx >= items.length) return;
        out[idx] = await fn(items[idx]);
      }
    }),
  );
  return out;
}

// ───── menu parsing ─────────────────────────────────────────────────────────

function buildMenuUrl(date: Date): string {
  return `${MENU_BASE}/${date.toISOString().slice(0, 10)}/alle.html`;
}

function parseGermanDate(s: string): Date | null {
  const m = /^(\d{2})\.(\d{2})\.(\d{4})$/.exec(s.trim());
  if (!m) return null;
  return new Date(`${m[3]}-${m[2]}-${m[1]}T00:00:00Z`);
}

// Strip and collect EU allergen / Mensa additive codes from text. Conservative:
// only matches "(…)" groups whose contents look entirely like codes, so
// arbitrary parenthetical asides ("(Mittagsangebot)", "(z.B. mit Fisch)")
// are left intact.
const CODE_RE = /^([0-9]{1,2}|[a-n](?:\.[0-9]{1,2})?)$/;
function extractCodes(text: string): {
  stripped: string;
  allergens: string[];
  additives: string[];
} {
  const allergens = new Set<string>();
  const additives = new Set<string>();
  const stripped = text.replace(/\s*\(([^)]+)\)/g, (full, body: string) => {
    const parts = body.split(',').map((p) => p.trim()).filter(Boolean);
    if (parts.length === 0 || !parts.every((p) => CODE_RE.test(p))) return full;
    for (const p of parts) {
      if (/^[0-9]/.test(p)) additives.add(p);
      else allergens.add(p);
    }
    return '';
  });
  return {
    stripped: stripped.replace(/\s+/g, ' ').trim(),
    allergens: [...allergens].sort(),
    additives: [...additives].sort((a, b) => Number(a) - Number(b)),
  };
}

function iconSlug(src: string | null | undefined): string {
  if (!src) return '';
  return src.split('/').pop()!.replace(/\.(png|jpe?g|svg)$/i, '').toLowerCase();
}

// "(Mittagsangebot)" → "Mittagsangebot"
function cleanNote(s: string | null): string | null {
  if (!s) return null;
  const t = s.trim().replace(/^\(|\)$/g, '').trim();
  return t || null;
}

interface Offer {
  servedOn: Date;
  canteen: string;
  category: string;
  rawTitle: string;       // exactly as on the page (with "(a.1,a,c)" markers)
  fullText: string;       // full bez cell text — kept compatible w/ existing UNIQUE(full_text)
  cleanTitle: string;     // title with markers removed
  description: string | null; // sides/components as a single cleaned string
  sides: string[];        // same as description, split on comma
  rawNote: string | null; // italic note exactly as on page, e.g. "(Mittagsangebot)"
  mealPeriod: string | null; // italic note with surrounding parens stripped
  allergens: string[];
  additives: string[];
  icons: string[];
}

// Split a cleaned description ("X, Y, Z") into individual side dishes.
// Comma is the reliable separator — verified against several days of HTML.
function splitSides(desc: string | null): string[] {
  if (!desc) return [];
  return desc.split(',').map((s) => s.trim()).filter(Boolean);
}

function parseMenuHtml(html: string, fallback: Date): Offer[] {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  if (!doc) return [];
  const offers: Offer[] = [];

  doc.querySelectorAll('table.sp_tab').forEach((node) => {
    const table = node as Element;
    const canteen = table.querySelector('th strong')?.textContent.trim() ?? '';
    if (!canteen) return;
    const dateStr = table.querySelector('.sp_date')?.textContent.trim() ?? '';
    const servedOn = parseGermanDate(dateStr) ?? fallback;

    table.querySelectorAll('tr').forEach((rowNode) => {
      const row = rowNode as Element;
      const typeCell = row.querySelector('.sp_typ');
      const bezCell = row.querySelector('.sp_bez');
      if (!typeCell || !bezCell) return;
      const rawTitle = bezCell.querySelector('strong')?.textContent.trim() ?? '';
      if (!rawTitle) return; // skip non-meal rows ("Last Minute" info, "Salatbuffet" hints)

      const category = typeCell.textContent.trim();
      const rawNote = bezCell.querySelector('i')?.textContent.trim() ?? null;
      const mealPeriod = cleanNote(rawNote);

      // fullText: stays compatible with the existing UNIQUE(full_text) constraint
      const fullText = bezCell.textContent.replace(/\s+/g, ' ').trim();

      // cleaner derivations
      const titleParse = extractCodes(rawTitle);
      const bezClone = bezCell.cloneNode(true) as Element;
      bezClone.querySelectorAll('i').forEach((n) => (n as Element).remove());
      const desc0 = bezClone.textContent.replace(/\s+/g, ' ').trim();
      const desc1 = desc0.startsWith(rawTitle) ? desc0.slice(rawTitle.length).trim() : desc0;
      const descParse = extractCodes(desc1);

      const allergens = [...new Set([...titleParse.allergens, ...descParse.allergens])].sort();
      const additives = [
        ...new Set([...titleParse.additives, ...descParse.additives]),
      ].sort((a, b) => Number(a) - Number(b));

      const icons = [...row.querySelectorAll('.sp_hin img')]
        .map((img) => iconSlug((img as Element).getAttribute('src')))
        .filter(Boolean);

      const description = descParse.stripped || null;
      offers.push({
        servedOn,
        canteen,
        category,
        rawTitle,
        fullText,
        cleanTitle: titleParse.stripped,
        description,
        sides: splitSides(description),
        rawNote,
        mealPeriod,
        allergens,
        additives,
        icons,
      });
    });
  });

  return offers;
}

interface DayResult {
  date: Date;
  ok: boolean;        // true only if the upstream returned valid HTML (200, parsed)
  offers: Offer[];
}

async function scrapeDay(date: Date): Promise<DayResult> {
  const url = buildMenuUrl(date);
  const iso = date.toISOString().slice(0, 10);
  const res = await fetchWithRetry(url);
  if (!res) return { date, ok: false, offers: [] };
  if (res.status === 404) {
    // Day exists in the calendar sense, but no plan was published → treat as
    // "successfully scraped, zero offers" so deactivation can clean up stale rows.
    log(`· ${iso} — no plan (404)`);
    return { date, ok: true, offers: [] };
  }
  if (!res.ok) {
    log(`✖ ${iso} — HTTP ${res.status}`);
    return { date, ok: false, offers: [] };
  }
  const html = await res.text();
  const offers = parseMenuHtml(html, date);
  log(`✓ ${iso} — ${offers.length} offers`);
  return { date, ok: true, offers };
}

// ───── price parsing ────────────────────────────────────────────────────────

// Maps canteen names (as they appear in the daily plan) to their per-canteen
// page slug. Pages without a price section are simply absent here.
const PRICE_PAGES: Record<string, string> = {
  'Zentralmensa': '/campusgastronomie/mensen/zentralmensa',
  'Mensa am Turm': '/campusgastronomie/mensen/studentenwerk-goettingen-mensa-am-turm',
  'CGiN': '/campusgastronomie/mensen/studierendenwerk-goettingen-cgin',
};

interface PriceRow {
  category: string;
  rawStudents: string | null;
  rawEmployees: string | null;
  rawGuests: string | null;
  studentsCents: number | null;
  employeesCents: number | null;
  guestsCents: number | null;
}

// "3,95 Euro" → 395; "3,95 / 4,95 Euro" → 395 (first); "---" → null
function parseEuroCents(s: string | null | undefined): number | null {
  if (!s) return null;
  const m = /(-?\d+),(\d{2})/.exec(s);
  if (!m) return null;
  return Number(m[1]) * 100 + Number(m[2]);
}

function parsePriceHtml(html: string): PriceRow[] {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  if (!doc) return [];
  const rows: PriceRow[] = [];

  // Price tables on the canteen pages have a header row "Studierende /
  // Bedienstete / Gäste" and then rows of label + 3 prices. There can be
  // many such tables on one page, separated by category headings.
  for (const tableNode of doc.querySelectorAll('table')) {
    const table = tableNode as Element;
    const trs = [...table.querySelectorAll('tr')];
    if (trs.length < 2) continue;
    const headerCells = [...(trs[0] as Element).querySelectorAll('th, td')]
      .map((c) => (c as Element).textContent.replace(/\s+/g, ' ').trim().toLowerCase());
    if (!headerCells.some((h) => h.includes('studierende'))) continue;

    for (const tr of trs.slice(1)) {
      const cells = [...(tr as Element).querySelectorAll('th, td')].map((c) =>
        (c as Element).textContent.replace(/ /g, ' ').replace(/\s+/g, ' ').trim(),
      );
      if (cells.length < 4) continue;
      const [category, students, employees, guests] = cells.slice(-4);
      if (!category || category.toLowerCase() === 'studierende') continue;
      rows.push({
        category,
        rawStudents: students || null,
        rawEmployees: employees || null,
        rawGuests: guests || null,
        studentsCents: parseEuroCents(students),
        employeesCents: parseEuroCents(employees),
        guestsCents: parseEuroCents(guests),
      });
    }
  }
  return rows;
}

async function scrapePrices(canteen: string): Promise<PriceRow[]> {
  const path = PRICE_PAGES[canteen];
  if (!path) return [];
  const res = await fetchWithRetry(`${ORIGIN}${path}`);
  if (!res || !res.ok) {
    log(`prices: ✖ ${canteen} — fetch failed`);
    return [];
  }
  const rows = parsePriceHtml(await res.text());
  log(`prices: ✓ ${canteen} — ${rows.length} rows`);
  return rows;
}

// ───── DB ───────────────────────────────────────────────────────────────────

const canteenIdCache = new Map<string, string>();

async function getOrCreateCanteenId(name: string): Promise<string> {
  const cached = canteenIdCache.get(name);
  if (cached) return cached;
  const { data, error } = await supabase
    .from('canteens')
    .upsert({ name }, { onConflict: 'name' })
    .select('id')
    .single();
  if (error) throw error;
  canteenIdCache.set(name, data!.id);
  return data!.id;
}

async function upsertMeal(o: Offer): Promise<string> {
  const { data, error } = await supabase
    .from('meals')
    .upsert(
      {
        title: o.rawTitle,        // unchanged for backwards compat (used as a key in image fns)
        full_text: o.fullText,    // unchanged: UNIQUE constraint = dedup key
        clean_title: o.cleanTitle,
        description: o.description,
        sides: o.sides,
        icons: o.icons,
        allergens: o.allergens,
        additives: o.additives,
      },
      { onConflict: 'full_text' },
    )
    .select('id')
    .single();
  if (error) throw error;
  return data!.id;
}

interface UpsertResult {
  // keys "<canteen_id>|<category>" of meal_dates we successfully wrote, per day
  seenByDay: Map<string, Set<string>>;
  ok: number;
  fail: number;
}

async function upsertOffers(offers: Offer[]): Promise<UpsertResult> {
  const seenByDay = new Map<string, Set<string>>();
  let ok = 0, fail = 0;
  for (const o of offers) {
    try {
      const [canteenId, mealId] = await Promise.all([
        getOrCreateCanteenId(o.canteen),
        upsertMeal(o),
      ]);
      const dayISO = o.servedOn.toISOString().slice(0, 10);
      const { error } = await supabase.from('meal_dates').upsert(
        {
          meal_id: mealId,
          canteen_id: canteenId,
          served_on: dayISO,
          category: o.category,
          note: o.rawNote,        // unchanged: keeps "(Mittagsangebot)" form for back-compat
          meal_period: o.mealPeriod,  // cleaned: "Mittagsangebot"
          deactivated_at: null,   // re-activate if it was previously gone
        },
        { onConflict: 'canteen_id,served_on,category', ignoreDuplicates: false },
      );
      if (error) throw error;
      let set = seenByDay.get(dayISO);
      if (!set) seenByDay.set(dayISO, (set = new Set()));
      set.add(`${canteenId}|${o.category}`);
      ok++;
    } catch (e) {
      fail++;
      log(`upsert error (${o.canteen} / ${o.category} / ${o.rawTitle}):`, e);
    }
  }
  return { seenByDay, ok, fail };
}

// For each day we successfully scraped, mark stale meal_date rows as
// deactivated. "Stale" = present in DB for that day, but not in the seen set
// from this run. Rows that are still seen get reactivated by upsertOffers.
async function deactivateStale(
  successfulDays: string[],
  seenByDay: Map<string, Set<string>>,
): Promise<number> {
  if (successfulDays.length === 0) return 0;
  const { data, error } = await supabase
    .from('meal_dates')
    .select('id, canteen_id, served_on, category, deactivated_at')
    .in('served_on', successfulDays);
  if (error) {
    log('deactivate: select error', error);
    return 0;
  }
  const stale: string[] = [];
  for (const row of data ?? []) {
    if (row.deactivated_at) continue; // already deactivated, skip
    const seen = seenByDay.get(row.served_on);
    const key = `${row.canteen_id}|${row.category}`;
    if (!seen || !seen.has(key)) stale.push(row.id);
  }
  if (stale.length === 0) return 0;
  const nowISO = new Date().toISOString();
  // Chunk to avoid PostgREST URL length limits on .in().
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

async function upsertPrices(canteen: string, rows: PriceRow[]): Promise<void> {
  if (rows.length === 0) return;
  const canteenId = await getOrCreateCanteenId(canteen);
  const payload = rows.map((r) => ({
    canteen_id: canteenId,
    category: r.category,
    price_students: r.rawStudents,
    price_employees: r.rawEmployees,
    price_guests: r.rawGuests,
    price_students_cents: r.studentsCents,
    price_employees_cents: r.employeesCents,
    price_guests_cents: r.guestsCents,
    updated_at: new Date().toISOString(),
  }));
  const { error } = await supabase
    .from('canteen_prices')
    .upsert(payload, { onConflict: 'canteen_id,category' });
  if (error) log(`prices upsert error (${canteen}):`, error);
}

// ───── HTTP entry ───────────────────────────────────────────────────────────

interface Body {
  days?: number;
  prices?: boolean; // default: true
}

Deno.serve(async (req) => {
  log('── invocation ──');

  let days = 14;
  let scrapePricesToo = true;
  try {
    const txt = (await req.text()).trim();
    if (txt) {
      // Backwards compatible: accept either a bare number or a JSON object.
      const n = Number(txt);
      if (Number.isFinite(n) && n > 0) {
        days = Math.min(60, Math.max(1, Math.floor(n)));
      } else {
        const body = JSON.parse(txt) as Body;
        if (typeof body.days === 'number' && body.days > 0) {
          days = Math.min(60, Math.max(1, Math.floor(body.days)));
        }
        if (typeof body.prices === 'boolean') scrapePricesToo = body.prices;
      }
    }
  } catch (e) {
    log('body parse warning, using defaults:', e);
  }

  const bgPromise = (async () => {
    try {
      const today = new Date();
      const jobDates = Array.from({ length: days }, (_, i) =>
        new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate() + i)),
      );
      log(`BG: scraping ${jobDates.length} day(s) at concurrency ${SCRAPE_CONCURRENCY}…`);

      const dayResults = await mapWithConcurrency(jobDates, SCRAPE_CONCURRENCY, scrapeDay);
      const allOffers = dayResults.flatMap((d) => d.offers);
      const successfulDays = dayResults.filter((d) => d.ok).map((d) => d.date.toISOString().slice(0, 10));
      log(`BG: ${allOffers.length} offers from ${successfulDays.length}/${dayResults.length} successful day(s); upserting…`);

      const { seenByDay, ok, fail } = await upsertOffers(allOffers);
      log(`BG: meal_dates upserted: ${ok} ok, ${fail} failed`);

      const deactivated = await deactivateStale(successfulDays, seenByDay);
      log(`BG: deactivated ${deactivated} stale meal_date row(s)`);

      if (scrapePricesToo) {
        log(`BG: scraping prices for ${Object.keys(PRICE_PAGES).length} canteen(s)…`);
        for (const canteen of Object.keys(PRICE_PAGES)) {
          try {
            const rows = await scrapePrices(canteen);
            await upsertPrices(canteen, rows);
          } catch (e) {
            log(`prices: ✖ ${canteen}`, e);
          }
        }
      }

      log('BG: finished run');
    } catch (e) {
      log('BG: fatal error', e);
    }
  })();

  // @ts-ignore EdgeRuntime is provided by Supabase
  EdgeRuntime?.waitUntil?.(bgPromise);

  return new Response(JSON.stringify({ accepted: true, days, prices: scrapePricesToo }), {
    status: 202,
    headers: { 'Content-Type': 'application/json' },
  });
});
