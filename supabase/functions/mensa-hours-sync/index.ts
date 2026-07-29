// supabase/functions/mensa-hours-sync/index.ts
//
// Mirrors /api/oeffnungszeiten into public.canteen_hours. The endpoint
// returns 13 entries (mensas + cafés). Each is keyed by a German short
// weekday (mo, di, mi, do, fr, sa) with `{tag}_auf` / `{tag}_zu` HH:MM
// fields; closed days carry the literal "geschlossen" sentinel and an
// empty `_zu`. Sunday is never returned — treated as closed.
//
// One row per (canteen, day_of_week) using ISO weekday numbering
// (Mon=1..Sun=7). Closed days carry NULL open/close times. The
// table is dense (always 7 rows per canteen) so consumers don't have
// to guess about absent rows.

import {
  fetchWithRetry,
  getOrCreateCanteenId,
  log,
  supabase,
} from '../_shared/supabase.ts';
import { CANTEEN_SLUGS, CANTEEN_EXTERNAL_IDS, resolveKnownCanteen } from '../_shared/canteens.ts';

const URL = 'https://app.studentenwerk-goettingen.de/api/oeffnungszeiten';

interface HoursEntry {
  name: string;
  // mo_auf / mo_zu / di_auf / di_zu / ... / sa_auf / sa_zu
  [k: string]: string;
}

const TAG_BY_ISO: Array<[number, string]> = [
  [1, 'mo'],
  [2, 'di'],
  [3, 'mi'],
  [4, 'do'],
  [5, 'fr'],
  [6, 'sa'],
];

function parseHHMM(s: string): string | null {
  const m = /^(\d{1,2}):(\d{2})$/.exec(s.trim());
  if (!m) return null;
  const h = Number(m[1]);
  const min = Number(m[2]);
  if (h > 23 || min > 59) return null;
  return `${h.toString().padStart(2, '0')}:${m[2]}:00`;
}

interface DayRow {
  canteen_id: string;
  day_of_week: number;
  open_time: string | null;
  close_time: string | null;
  updated_at: string;
}

Deno.serve(async () => {
  log('── mensa-hours-sync ──');
  const res = await fetchWithRetry(URL, 'application/json');
  if (!res || !res.ok) return new Response('upstream fetch failed', { status: 502 });
  let entries: HoursEntry[];
  try {
    entries = (await res.json()) as HoursEntry[];
  } catch (e) {
    log('parse error', e);
    return new Response('upstream parse failed', { status: 502 });
  }
  log(`fetched ${entries.length} location entries`);

  const nowISO = new Date().toISOString();
  const rows: DayRow[] = [];
  for (const e of entries) {
    if (!e.name) continue;
    // This endpoint is the source of the cafés, which aren't in
    // CANTEEN_SLUGS — so auto-create stays on here. Name folding in
    // getOrCreateCanteenId still keeps casing drift from forking a row.
    const canteenId = await getOrCreateCanteenId(e.name);
    if (!canteenId) continue;
    // For the four "real" mensas, also backfill slug + external_id in
    // case the migration didn't catch them (e.g. if the canteen was
    // newly auto-created from a previous run by name only).
    const canonical = resolveKnownCanteen(e.name);
    if (canonical && CANTEEN_SLUGS[canonical]) {
      await supabase
        .from('canteens')
        .update({
          slug: CANTEEN_SLUGS[canonical],
          external_id: CANTEEN_EXTERNAL_IDS[canonical] ?? null,
        })
        .eq('id', canteenId);
    }
    for (const [iso, tag] of TAG_BY_ISO) {
      const auf = (e[`${tag}_auf`] ?? '').trim();
      const zu = (e[`${tag}_zu`] ?? '').trim();
      const closed = !auf || /^geschlossen$/i.test(auf);
      rows.push({
        canteen_id: canteenId,
        day_of_week: iso,
        open_time: closed ? null : parseHHMM(auf),
        close_time: closed ? null : parseHHMM(zu),
        updated_at: nowISO,
      });
    }
    // Sunday: API omits it, treat as always closed.
    rows.push({
      canteen_id: canteenId,
      day_of_week: 7,
      open_time: null,
      close_time: null,
      updated_at: nowISO,
    });
  }

  const { error } = await supabase
    .from('canteen_hours')
    .upsert(rows, { onConflict: 'canteen_id,day_of_week' });
  if (error) {
    log('upsert error', error);
    return new Response(`upsert failed: ${error.message}`, { status: 500 });
  }
  log(`upserted ${rows.length} hour rows for ${entries.length} canteens`);
  return new Response(JSON.stringify({ ok: true, locations: entries.length, rows: rows.length }), {
    headers: { 'Content-Type': 'application/json' },
  });
});
