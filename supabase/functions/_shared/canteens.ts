// Source-of-truth maps for the four canteens the menu API exposes.
// Used by mensa-occupancy-sync to look up canteens by external id and by
// mensa-menu-sync to assign slugs to newly-created canteens.

export const CANTEEN_SLUGS: Record<string, string> = {
  'Zentralmensa': 'zentral',
  'Mensa am Turm': 'turm',
  'CGiN': 'cgin',
  'Bistro HAWK': 'hawk',
};

// /api/frequenz response keys these by integer id; we mirror the same.
export const CANTEEN_EXTERNAL_IDS: Record<string, number> = {
  'Zentralmensa': 4014,
  'Mensa am Turm': 4155,
  'CGiN': 4209,
  'Bistro HAWK': 4272,
};

// The menu API's <mensa> elements carry no id — only a free-text `name`
// attribute — so the display name is the only join key we get, and upstream
// does change it. Observed so far:
//   2026-07-24  a second <mensa name="CGIN"> block appeared alongside "CGiN",
//               carrying the Friday burger counters.
// Case and stray whitespace are handled generically by normaliseCanteenName;
// this map is for variants that differ structurally. Add a line here when a
// new one shows up — the sync reports unknown names rather than guessing.
export const CANTEEN_NAME_ALIASES: Record<string, string> = {
  // normalised (lowercase) form → canonical name in CANTEEN_SLUGS
};

/** Lowercase, trim, collapse internal whitespace. */
export function normaliseCanteenName(name: string): string {
  return name.trim().replace(/\s+/g, ' ').toLowerCase();
}

const CANONICAL_BY_NORMALISED = new Map<string, string>(
  Object.keys(CANTEEN_SLUGS).map((n) => [normaliseCanteenName(n), n]),
);

/**
 * Map an upstream <mensa name="…"> to the exact spelling CANTEEN_SLUGS and
 * CANTEEN_EXTERNAL_IDS are keyed by, or null when we don't recognise it.
 * Null means "report it", never "create it" — silently minting a canteen is
 * what produced the duplicate CGiN in the picker.
 */
export function resolveKnownCanteen(rawName: string): string | null {
  const normalised = normaliseCanteenName(rawName);
  const aliased = CANTEEN_NAME_ALIASES[normalised];
  if (aliased) return aliased;
  return CANONICAL_BY_NORMALISED.get(normalised) ?? null;
}
