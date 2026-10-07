// Shared text-cleanup helpers for the menu scraper.
//
// The upstream API leaks the same garbage shapes the HTML scrape did:
// allergen codes embedded in parentheses inside the title (`Vegane Rote-Bete-Puffer (a.1,a)`)
// and inside the sides field (`Vegane Broccolisauce (3,f), Wildreis…`),
// so this is the same regex set the previous Kotlin sanitiser used,
// just moved upstream so the DB stores clean rows.

// Allergen letters a-n with optional sub-codes (a.1, h.7) plus
// additive digits 1-11. Used both for code recognition (in `<zusatzstoffe>`)
// and for fragment cleanup (sides that ended up as bare codes after a
// naïve comma-split).
export const CODE_RE = /^([0-9]{1,2}|[a-n](?:\.[0-9]{1,2})?)$/i;

// Strip all parenthesised groups from a title for display. The API title
// looks like "Gebratenes Strohschweinschnitzel (a.1,a,c,g,i,3)" — we want
// "Gebratenes Strohschweinschnitzel" for the clean_title column.
export function stripAllergenParens(s: string): string {
  return s.replace(/\s*\([^)]*\)/g, '').replace(/\s+/g, ' ').trim();
}

// "3,95" → 395 cents; "3,95 / 4,95" → 395 (first); empty/dashes → null
export function parseEuroCents(s: string | null | undefined): number | null {
  if (!s) return null;
  const m = /(-?\d+),(\d{2})/.exec(s);
  if (!m) return null;
  return Number(m[1]) * 100 + Number(m[2]);
}

// Strip "Euro"/"€"/whitespace; collapse "---" / "—" / empty to null.
export function normalisePriceText(s: string | null | undefined): string | null {
  if (s == null) return null;
  const cleaned = s
    .replace(/ /g, ' ')
    .replace(/€/g, '')
    .replace(/\bEuro\b/gi, '')
    .replace(/\s+/g, ' ')
    .trim();
  if (!cleaned) return null;
  if (/^[-–—\s]+$/.test(cleaned)) return null;
  return cleaned;
}
