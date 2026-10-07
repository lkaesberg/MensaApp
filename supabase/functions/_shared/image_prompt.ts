// Prompts for the generic meal photos (smooth-endpoint).
//
// The original prompt was one template for everything — "main food on a
// plate, sides in small bowls, on a white plastic tray" — fed only the title.
// So a side counter's "Frisches Möhrengemüse" became a carrot main course
// with invented companion bowls, a dessert landed on a dinner plate, and the
// real accompaniments ("Vegane Jägersauce, Zitronenecke") never reached the
// model at all. The framing now follows the course and the listed sides are
// part of the prompt. The white background / tray look is kept so new photos
// sit next to the existing ones.

import { Course, isNonDishEntry } from './menu.ts';
import { stripAllergenParens } from './text.ts';

export interface PromptMeal {
  /** Clean German title (allergen codes stripped). */
  title: string;
  titleEn?: string | null;
  course?: Course | string | null;
  sides?: string[] | null;
}

const BASE = 'High-quality food photo on a plain white background, German university canteen style.';
const CLOSING = 'No text, no labels, no people, no hands.';
const MAX_SIDES = 4;

// Optional extras ("zusätzlich Geflügelwürstchen") and the unspecified
// HAWK "Tagesdessert" aren't on the plate in a way the model can draw.
const SKIP_SIDE_RE = /^(zusätzlich|additional)\b|tagesdessert|dessert of the day/i;

function dishName(m: PromptMeal): string {
  const en = m.titleEn ? stripAllergenParens(m.titleEn) : '';
  return en && en.toLowerCase() !== m.title.toLowerCase() ? `${m.title} (${en})` : m.title;
}

/** null when the entry isn't food (closed-day placeholder) or has no title. */
export function buildImagePrompt(m: PromptMeal): string | null {
  const title = m.title.trim();
  if (!title || isNonDishEntry(title, '')) return null;
  const dish = dishName({ ...m, title });
  const sides = (m.sides ?? []).filter((s) => s && !SKIP_SIDE_RE.test(s)).slice(0, MAX_SIDES);

  switch (m.course) {
    case 'side':
      return `${BASE} A single side-dish portion in a small white bowl: ${dish}. ` +
        `Only this one item — no main course, no tray, no other bowls. ${CLOSING}`;
    case 'salad':
      return `${BASE} A single portion of salad in a white bowl: ${dish}. ` +
        `Only this salad — no other dishes, no tray. ${CLOSING}`;
    case 'dessert': {
      const topping = sides.length ? `, served with ${sides[0]}` : '';
      return `${BASE} A single dessert portion in a small glass bowl: ${dish}${topping}. ` +
        `Only this dessert — no other dishes, no tray. ${CLOSING}`;
    }
    case 'soup': {
      const beside = sides.length ? ` Next to the bowl: ${sides.join('; ')}.` : '';
      return `${BASE} A deep white bowl of ${dish}.${beside} ` +
        `No other food. ${CLOSING}`;
    }
    default: {
      const withSides = sides.length
        ? ` Next to it on the tray, on the plate or in small bowls: ${sides.join('; ')}.`
        : ' No side dishes.';
      return `${BASE} A white plastic tray with the main dish on a plate: ${dish}.${withSides} ` +
        `Show only the listed food — no extra bowls, drinks or cutlery. ${CLOSING}`;
    }
  }
}
