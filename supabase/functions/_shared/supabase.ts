// Supabase service-role client shared across mensa-* sync functions.
// Service role is required for upserts since RLS blocks anon writes.

// deno-lint-ignore-file no-explicit-any
import { createClient, SupabaseClient } from 'https://esm.sh/@supabase/supabase-js@2.39.6';
import { resolveKnownCanteen } from './canteens.ts';

const SUPABASE_URL = Deno.env.get('SUPABASE_URL');
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
  throw new Error('Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY');
}

export const supabase: SupabaseClient = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
  auth: { persistSession: false },
});

export const log = (msg: string, ...args: any[]) =>
  console.log(`${new Date().toISOString()}  ${msg}`, ...args);

export const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

// ───── HTTP fetch with retry/backoff ─────
const USER_AGENT = 'MensaApp-Scraper/2.0 (+https://github.com/larskaesberg)';
const FETCH_TIMEOUT_MS = 15_000;
const FETCH_ATTEMPTS = 3;

export async function fetchWithRetry(url: string, accept = 'application/json'): Promise<Response | null> {
  let lastErr: unknown;
  for (let i = 0; i < FETCH_ATTEMPTS; i++) {
    try {
      const ctrl = new AbortController();
      const timer = setTimeout(() => ctrl.abort(), FETCH_TIMEOUT_MS);
      const res = await fetch(url, {
        headers: { 'User-Agent': USER_AGENT, Accept: accept },
        signal: ctrl.signal,
      });
      clearTimeout(timer);
      if (res.status === 404) return res;
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

export async function mapWithConcurrency<T, U>(
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

// Resolve canteen_id by name. The migration pre-creates the four mensas
// and backfills their slugs/external_ids. For new entries (e.g. cafés
// from /api/oeffnungszeiten) we upsert by name.
const canteenIdCache = new Map<string, string>();

// `canteens.name` is UNIQUE but case-sensitive, so any casing drift upstream
// forks a brand-new canteen — which then has no slug, no external_id, no
// opening hours and no occupancy, and shows up as a duplicate in the picker.
// This is not hypothetical: on 2026-07-24 the menu API started emitting a
// second <mensa name="CGIN"> block (Friday burger counters) alongside the
// regular "CGiN" one, and it forked.
//
// Two layers of defence, because the menu API gives us no canteen id — the
// free-text name is the only join key there is:
//
//   1. resolveKnownCanteen() folds case/whitespace and consults an explicit
//      alias map, so recognised canteens always land on one row.
//   2. `allowCreate` is opt-in. The menu sync passes false: an unrecognised
//      <mensa name> is reported and skipped, never turned into a canteen.
//      The hours sync passes true — that endpoint is where the cafés
//      legitimately come from, and they're not in CANTEEN_SLUGS.
export interface ResolveCanteenOptions {
  /** Create a row when the name matches nothing on record. Default true. */
  allowCreate?: boolean;
}

export async function getOrCreateCanteenId(
  rawName: string,
  opts: ResolveCanteenOptions = {},
): Promise<string | null> {
  const { allowCreate = true } = opts;
  const cacheKey = `${allowCreate}|${rawName}`;
  const cached = canteenIdCache.get(cacheKey);
  if (cached) return cached;

  const known = resolveKnownCanteen(rawName);
  const name = known ?? rawName.trim().replace(/\s+/g, ' ');
  if (!known && !allowCreate) return null;

  // Case-insensitive lookup before create, so casing drift on canteens that
  // aren't in CANTEEN_SLUGS (the cafés) doesn't fork either. ilike treats
  // % and _ as wildcards — canteen names are plain text, but escape anyway.
  const pattern = name.replace(/([\\%_])/g, '\\$1');
  const { data: existing, error: findErr } = await supabase
    .from('canteens')
    .select('id')
    .ilike('name', pattern)
    .limit(1);
  if (findErr) throw findErr;
  if (existing && existing.length > 0) {
    canteenIdCache.set(cacheKey, existing[0].id);
    return existing[0].id;
  }

  if (!allowCreate) return null;

  const { data, error } = await supabase
    .from('canteens')
    .upsert({ name }, { onConflict: 'name' })
    .select('id')
    .single();
  if (error) throw error;
  canteenIdCache.set(cacheKey, data!.id);
  return data!.id;
}
