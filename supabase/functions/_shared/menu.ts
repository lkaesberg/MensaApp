// Course classification and <essen2> parsing for the menu sync.
//
// The upstream API flattens every counter into the same <speise> shape, but
// the counters mean very different things:
//
//   • a main dish:     essen = the dish, essen2 = its accompaniments
//                      ("Sauce Cafe de Paris, Zitronenecke, Herzogin-Kartoffeln")
//   • a side counter:  essen = one option, essen2 = the *other* options
//                      (Mensa am Turm "Stärkebeilage": "Senf-Kartoffeln" +
//                       "Pommes frites, Tomatennudeln, Kräuterpüree")
//   • a dessert:       essen = one dessert, essen2 = "mit <topping> oder <other
//                      dessert> mit <topping> oder …"
//
// and essen2 is comma-separated *including* connector words: "auf, Curryreis",
// "zusätzlich, Geflügelwürstchen", "Brombeeren, und, Schokoraspeln", and the
// English variant puts every connector between commas ("with, vanilla sauce,
// or, fruit quark"). A naïve comma split turned those into "auf" / "und"
// chips and one giant "mit … oder … oder …" chip.
//
// The Kotlin client mirrors classifyCourse() in data/MenuStructure.kt as a
// fallback for rows synced before the `course` column existed — keep the two
// in step.

import { CODE_RE } from './text.ts';

export type Course = 'main' | 'soup' | 'side' | 'salad' | 'dessert';
export type Lang = 'de' | 'en';

/** Course from the upstream category (<preis>), e.g. "Stärkebeilage" → side. */
export function classifyCourse(category: string): Course {
  const c = category.toLowerCase();
  if (/dessert|nachtisch|nachspeise/.test(c)) return 'dessert';
  if (/beilage/.test(c)) return 'side';
  if (/salat/.test(c)) return 'salad';
  if (/eintopf|suppe/.test(c)) return 'soup';
  return 'main';
}

/**
 * Placeholder rows that aren't food. On closed days the API emits a single
 * <speise> with an empty <preis> and essen "Heute leider geschlossen"; stored
 * as a meal it showed up as a dish — with a generated photo.
 */
export function isNonDishEntry(title: string, category: string): boolean {
  if (/^\s*(heute\s+)?(leider\s+)?(geschlossen|closed)\b/i.test(title)) return true;
  return !category.trim() && /\b(geschlossen|closed)\b/i.test(title);
}

export interface Accompaniments {
  /** Served with the dish itself (for a dessert: its topping). */
  sides: string[];
  /** Other options at the same counter (side counters, desserts, buffets). */
  alternatives: string[];
}

type Connector = 'with' | 'or' | 'and' | 'on' | 'extra';

const CONNECTOR_WORDS: Record<string, Connector> = {
  mit: 'with', with: 'with',
  oder: 'or', or: 'or',
  und: 'and', and: 'and',
  auf: 'on', on: 'on',
  'zusätzlich': 'extra', additional: 'extra',
};

const WORDS: Record<Lang, Record<Exclude<Connector, 'extra'>, string>> = {
  de: { with: 'mit', or: 'oder', and: 'und', on: 'auf' },
  en: { with: 'with', or: 'or', and: 'and', on: 'on' },
};

// Leading connector glued to an item: "mit veganer Vanillesauce", "with creamy herb sauce".
const LEADING_CONNECTOR_RE = /^(mit|with|oder|or|und|and|auf|on|zusätzlich|additional)\s+(.+)$/i;
// Inline alternative inside one comma token: "Ziegenkäse-Sauce oder Tomaten-Sugo".
const INLINE_OR_RE = /\s+(?:oder|or)\s+/i;
// A topping that belongs to the option before it on a side/salad counter
// ("Blattsalatmix, Frenchdressing", "Penne Rigate, Kräuterpesto").
const CONDIMENT_RE = /(dressing|vinaigrette|pesto|dip)\b/i;
// In a main's side list only dressings are folded into the salad before them;
// a dip or pesto there is usually meant for the main itself.
const DRESSING_RE = /(dressing|vinaigrette)\b/i;
const SALAD_RE = /salat|salad|slaw/i;
// After "und" in a dessert list: a standalone dessert rather than another topping.
const DESSERT_NOUN_RE =
  /dessert|joghurt|yog(h)?urt|quark|pudding|creme|cream|mousse|kuchen|cake|grütze|kompott|compote|götterspeise|jelly|jello|salat|salad|brei|porridge/i;

type Token = { kind: 'item'; text: string } | { kind: 'conn'; c: Connector; word: string };

function tidy(s: string): string {
  return s.replace(/\s+/g, ' ').replace(/\s+([,.;:])/g, '$1').replace(/^[\s,.;:]+|[\s,.;:]+$/g, '').trim();
}

function tokenize(raw: string): Token[] {
  const stripped = raw.replace(/\s*\([^)]*\)/g, '').replace(/\s+/g, ' ');
  const out: Token[] = [];
  for (const part of stripped.split(',')) {
    const p = tidy(part);
    if (!p || CODE_RE.test(p) || p.startsWith('(') || p.endsWith(')')) continue;
    const bare = CONNECTOR_WORDS[p.toLowerCase()];
    if (bare) {
      out.push({ kind: 'conn', c: bare, word: p });
      continue;
    }
    let rest = p;
    const lead = LEADING_CONNECTOR_RE.exec(p);
    if (lead) {
      out.push({ kind: 'conn', c: CONNECTOR_WORDS[lead[1].toLowerCase()], word: lead[1] });
      rest = lead[2];
    }
    rest.split(INLINE_OR_RE).forEach((piece, i) => {
      if (i > 0) out.push({ kind: 'conn', c: 'or', word: 'oder' });
      const t = tidy(piece);
      if (t) out.push({ kind: 'item', text: t });
    });
  }
  return out;
}

/**
 * Split <essen2> (or <essen2_eng>) into accompaniments and alternatives,
 * interpreting the connector words according to the counter's course.
 */
export function parseAccompaniments(
  raw: string | null | undefined,
  course: Course,
  lang: Lang = 'de',
): Accompaniments {
  const sides: string[] = [];
  const alternatives: string[] = [];
  if (!raw || !raw.trim()) return { sides, alternatives };
  const w = WORDS[lang];
  const tokens = tokenize(raw);

  // A dessert line without any "oder" is just the dessert plus its toppings.
  const mode = course === 'side' || course === 'salad'
    ? 'counter'
    : course === 'dessert' && tokens.some((t) => t.kind === 'conn' && t.c === 'or')
    ? 'dessert'
    : 'main';

  // Where the next item goes; `glue` extends the last item written there.
  let target: string[] = sides;
  let pending = null as { c: Connector; word: string } | null;
  let sawCombo = false;

  const glue = (sep: string, text: string): boolean => {
    if (target.length === 0) return false;
    target[target.length - 1] += sep + text;
    return true;
  };

  for (const tok of tokens) {
    if (tok.kind === 'conn') {
      // "oder, mit …" → the "or" decides the structure; otherwise last wins.
      pending = pending?.c === 'or' ? pending : { c: tok.c, word: tok.word };
      continue;
    }
    const text = tok.text;
    const conn = pending?.c ?? null;
    const word = pending?.word ?? '';
    pending = null;
    const first = sides.length === 0 && alternatives.length === 0;

    if (conn === 'extra') {
      // "zusätzlich, Geflügelwürstchen" — an optional add-on, keep the cue.
      target.push(`${word.toLowerCase()} ${text}`);
      continue;
    }

    if (mode === 'main') {
      if (first) {
        target.push(text); // after a leading "auf"/"mit" it's still a plain side
      } else if (conn === 'on') {
        // Buffet counters list "<dish> auf <base>" pairs (Beats&Bites): each
        // pair is a dish of its own, and so is everything after the first.
        if (!sawCombo) {
          const prev = target.pop()!;
          sawCombo = true;
          target = alternatives;
          target.push(`${prev} ${w.on} ${text}`);
        } else {
          glue(` ${w.on} `, text);
        }
      } else if (conn === 'or') {
        glue(` ${w.or} `, text); // sauce choice: "A oder B"
      } else if (conn === 'with' || conn === 'and') {
        glue(` ${w[conn]} `, text);
      } else if (!conn && DRESSING_RE.test(text) && SALAD_RE.test(target[target.length - 1] ?? '')) {
        glue(` ${w.with} `, text); // "Blattsalatmix, Frenchdressing"
      } else {
        target.push(text);
      }
      continue;
    }

    if (mode === 'dessert') {
      if (first && conn === 'with') {
        sides.push(text); // "mit veganer Vanillesauce" tops the headline dessert
      } else if (first || conn === 'or' || (conn === 'and' && DESSERT_NOUN_RE.test(text))) {
        // New option. "und" usually adds a topping, but "…, und, Kokos-Joghurt
        // Dessert" introduces another dessert.
        target = alternatives;
        target.push(text);
      } else if (conn === 'with' || conn === 'and') {
        glue(` ${w[conn]} `, text);
      } else {
        glue(', ', text); // "Schokopudding mit Vanillesauce, Mandelblättchen"
      }
      continue;
    }

    // Side / salad counter: every item is an option of its own.
    if (first && conn === 'with') {
      sides.push(text);
      continue;
    }
    target = alternatives;
    if (conn === 'with' || conn === 'and') {
      if (!glue(` ${w[conn]} `, text)) target.push(text);
    } else if (!conn && CONDIMENT_RE.test(text) && target.length > 0) {
      glue(` ${w.with} `, text); // "Blattsalatmix, Frenchdressing"
    } else {
      target.push(text);
    }
  }

  return { sides: dedupe(sides), alternatives: dedupe(alternatives) };
}

function dedupe(xs: string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const x of xs.map(tidy)) {
    const k = x.toLowerCase();
    if (!x || seen.has(k)) continue;
    seen.add(k);
    out.push(x);
  }
  return out;
}

// ───── Real side dishes ────────────────────────────────────────────────────
//
// A main's side list mixes the actual sides with sauces, dips and garnish
// ("Remouladensauce, Zitronenecke, Butterkartoffeln, Fingermöhren"). The
// sides overview only wants what you'd pick as a side on its own. German
// compounds carry their meaning in the last part — "Gemüsesauce" is a sauce,
// "Pestokartoffeln" are potatoes — so the check looks at word endings, and
// only in the part before "mit"/"in", so "Blattsalatmix mit Frenchdressing"
// stays a salad. Tuned against three weeks of all four canteens (151 kept,
// 108 dropped). German names only: the English ones are too inconsistent.
// Mirrored in MenuStructure.kt — keep the two in step.
const CONDIMENT_END_RE =
  /(sauce|soße|sosse|sugo|ketchup|dip|dressing|vinaigrette|remoulade|mayonnaise|mayo|aioli|pesto|salsa|chutney|relish|jus|schmelze|hollandaise|topping|senf|quark|tzatziki|ragout|glasur|preiselbeeren)$/;
const GARNISH_RE =
  /^(frisch[a-zäöüß]*\s+)?(petersilie|schnittlauch|koriander|kresse|rucola|minze|zitronenecke|zitronen?|limetten?[a-zäöüß]*|frühlingszwiebel[a-zäöüß]*|röstzwiebeln|tomaten|gurken|in)$/;
const NOT_A_SIDE_RE = /sprossen|parmesan|umlegt|dessert|brötchen|brot|baguette|ciabatta|naan/;
const SIDE_HEAD_SPLIT_RE = /\s+(?:mit|in)\s+/;

export function isRealSide(name: string): boolean {
  const t = name.trim().toLowerCase();
  if (!t || /^zusätzlich(\s|$)/.test(t)) return false;
  const head = t.split(SIDE_HEAD_SPLIT_RE)[0].trim();
  if (GARNISH_RE.test(head) || NOT_A_SIDE_RE.test(head)) return false;
  return !head.split(/\s+/).some((w) => CONDIMENT_END_RE.test(w));
}

export interface PhotoItem {
  name: string;
  course: 'side' | 'salad' | 'dessert';
}

/**
 * What a meal row contributes to the app's sides and desserts screens, each
 * item photographed on its own: every option of a side/salad/dessert counter
 * and the real sides of a main. Counter options are named exactly like
 * MenuStructure.counterOptions(…, De) — "<title> mit <sides>", then the
 * alternatives — because the photo path is derived from the name.
 */
export function photoItemsOf(meal: {
  course: string | null;
  clean_title: string | null;
  title: string;
  sides: string[] | null;
  alternatives: string[] | null;
}): PhotoItem[] {
  const title = (meal.clean_title?.trim() || meal.title.replace(/\s*\([^)]*\)/g, '')).trim();
  const sides = meal.sides ?? [];
  switch (meal.course) {
    case 'side':
    case 'salad':
    case 'dessert': {
      const head = sides.length ? `${title} mit ${sides.join(', ')}` : title;
      const course = meal.course === 'dessert' ? 'dessert' : null;
      return [...new Set([head, ...(meal.alternatives ?? [])])]
        .filter(Boolean)
        .map((name) => ({ name, course: course ?? (/salat|slaw|salad/i.test(name) ? 'salad' : 'side') }));
    }
    default:
      return sides.filter(isRealSide).map((name) => ({ name, course: /salat|slaw|salad/i.test(name) ? 'salad' : 'side' }));
  }
}

/** Storage file name for a side's photo — same ASCII-only slug as the meal photos. */
export function sideImageFile(name: string): string {
  return `${name.trim().replace(/\W+/g, '_').toLowerCase()}.jpg`;
}
