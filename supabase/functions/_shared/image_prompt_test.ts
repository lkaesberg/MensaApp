// deno test supabase/functions/_shared/image_prompt_test.ts
import { assert, assertEquals, assertStringIncludes } from 'https://deno.land/std@0.224.0/assert/mod.ts';
import { buildImagePrompt } from './image_prompt.ts';

Deno.test('main dish lists its real sides and forbids invented ones', () => {
  const p = buildImagePrompt({
    title: 'Veganes Schnitzel',
    titleEn: 'vegan schnitzel (3,a.1,a)',
    course: 'main',
    sides: ['Vegane Jägersauce', 'Zitronenecke'],
  })!;
  assertStringIncludes(p, 'main dish on a plate: Veganes Schnitzel (vegan schnitzel)');
  assertStringIncludes(p, 'Vegane Jägersauce; Zitronenecke');
  assertStringIncludes(p, 'no extra bowls');
});

Deno.test('side counter is a single bowl, no tray', () => {
  const p = buildImagePrompt({ title: 'Senf-Kartoffeln', course: 'side', sides: [] })!;
  assertStringIncludes(p, 'single side-dish portion in a small white bowl: Senf-Kartoffeln');
  assertStringIncludes(p, 'no tray');
  assert(!p.includes('main dish on a plate'));
});

Deno.test('dessert gets its topping, soup drops optional extras', () => {
  assertStringIncludes(
    buildImagePrompt({ title: 'Hausgemachte Rote Grütze', course: 'dessert', sides: ['veganer Vanillesauce'] })!,
    'glass bowl: Hausgemachte Rote Grütze, served with veganer Vanillesauce',
  );
  const soup = buildImagePrompt({
    title: 'Italienischer Gnocchieintopf',
    course: 'soup',
    sides: ['Ciabattabrot', 'zusätzlich Geflügelwürstchen Wiener Art'],
  })!;
  assertStringIncludes(soup, 'Next to the bowl: Ciabattabrot.');
  assert(!soup.includes('Geflügelwürstchen'));
});

Deno.test('legacy rows without a course fall back to the main framing', () => {
  assertStringIncludes(buildImagePrompt({ title: 'Kaiserschmarrn', course: null })!, 'main dish on a plate');
});

Deno.test('closed-day placeholder gets no photo', () => {
  assertEquals(buildImagePrompt({ title: 'Heute leider geschlossen' }), null);
  assertEquals(buildImagePrompt({ title: '  ' }), null);
});
