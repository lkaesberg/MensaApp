// deno test supabase/functions/_shared/menu_test.ts
// Fixtures are verbatim <essen2>/<essen2_eng> values from the live API.
import { assertEquals } from 'https://deno.land/std@0.224.0/assert/mod.ts';
import { classifyCourse, isNonDishEntry, isRealSide, parseAccompaniments, photoItemsOf, sideImageFile } from './menu.ts';

Deno.test('classifyCourse maps the upstream counters', () => {
  assertEquals(classifyCourse('Stärkebeilage'), 'side');
  assertEquals(classifyCourse('Gemüsebeilage'), 'side');
  assertEquals(classifyCourse('Salat'), 'salad');
  assertEquals(classifyCourse('Extra Salat'), 'salad');
  assertEquals(classifyCourse('Dessert'), 'dessert');
  assertEquals(classifyCourse('Dessertbuffet Bistro HAWK'), 'dessert');
  assertEquals(classifyCourse('Spezial Eintopf HAWK'), 'soup');
  assertEquals(classifyCourse('Nds-Menü MaT Turm-Menü Vegetarisch Kombi'), 'main');
  // A sweet *main* (3,00 €), not a dessert.
  assertEquals(classifyCourse('Green Spezial Süß'), 'main');
});

Deno.test('isNonDishEntry catches the closed-day placeholder only', () => {
  assertEquals(isNonDishEntry('Heute leider geschlossen', ''), true);
  assertEquals(isNonDishEntry('Geschlossen', ''), true);
  assertEquals(isNonDishEntry('Veganes Schnitzel', 'Turm Vegan'), false);
});

Deno.test('main: plain comma list stays a side list', () => {
  assertEquals(
    parseAccompaniments('Sauce Cafe de Paris (a.1,a,f,g), Zitronenecke, Herzogin-Kartoffeln (3), Püree aus jungen Erbsen', 'main'),
    { sides: ['Sauce Cafe de Paris', 'Zitronenecke', 'Herzogin-Kartoffeln', 'Püree aus jungen Erbsen'], alternatives: [] },
  );
});

Deno.test('main: leading "auf" is not a side', () => {
  assertEquals(
    parseAccompaniments('auf, Curryreis mit Ananasstücken, Frühlingszwiebelröllchen', 'main'),
    { sides: ['Curryreis mit Ananasstücken', 'Frühlingszwiebelröllchen'], alternatives: [] },
  );
  assertEquals(
    parseAccompaniments('on, rice with curry and diced pineapple, spring onion', 'main', 'en'),
    { sides: ['rice with curry and diced pineapple', 'spring onion'], alternatives: [] },
  );
});

Deno.test('main: leading "mit" and sauce choices', () => {
  assertEquals(
    parseAccompaniments('mit hausgemachte Pflaumen-Pfeffersauce (3,a.1,a,f) oder hausgemachte süß-saure Sauce (1,3,a.1,a,f,k)', 'main'),
    { sides: ['hausgemachte Pflaumen-Pfeffersauce oder hausgemachte süß-saure Sauce'], alternatives: [] },
  );
  assertEquals(
    parseAccompaniments('goat cheese honey sauce (g), or, fruity tomato basil sugo, tomato  cucumber salad', 'main', 'en'),
    { sides: ['goat cheese honey sauce or fruity tomato basil sugo', 'tomato cucumber salad'], alternatives: [] },
  );
});

Deno.test('main: dressing joins the salad before it', () => {
  assertEquals(
    parseAccompaniments('Ratatouillesauce (j), Spiralnudeln bunt (a.1,a), Blattsalatmix, Frenchdressing (3,c,g,j)', 'main'),
    { sides: ['Ratatouillesauce', 'Spiralnudeln bunt', 'Blattsalatmix mit Frenchdressing'], alternatives: [] },
  );
});

Deno.test('soup: "zusätzlich" marks an optional extra', () => {
  assertEquals(
    parseAccompaniments('Mehrkornbrötchen (a.1,a,a.4,a.2,a.3,k), zusätzlich, Geflügelwürstchen  Wiener Art (1,3,i)', 'soup'),
    { sides: ['Mehrkornbrötchen', 'zusätzlich Geflügelwürstchen Wiener Art'], alternatives: [] },
  );
});

Deno.test('main: buffet "X auf Y" pairs become alternatives', () => {
  const { sides, alternatives } = parseAccompaniments(
    'auf, Harissa Minz-Couscous (1,3,a.1,a), Vegane Cauli Wings Barbecue (3,a.1,a), auf, Zartweizen mit buntem Gemüse (a.1,a), ' +
      'Veganer marinierter Tofu gebraten (a.1,a,f,k,l), auf, Gebratene Chinanudeln mit Edamame (a,a.1,f), auf, Curryreis, ' +
      'Veganes Schnitzel (3,a.1,a) mit Vegane Champignons a la creme mit Gemüsestreifen (3,f,i)',
    'main',
  );
  assertEquals(sides, ['Harissa Minz-Couscous']);
  assertEquals(alternatives, [
    'Vegane Cauli Wings Barbecue auf Zartweizen mit buntem Gemüse',
    'Veganer marinierter Tofu gebraten auf Gebratene Chinanudeln mit Edamame auf Curryreis',
    'Veganes Schnitzel mit Vegane Champignons a la creme mit Gemüsestreifen',
  ]);
});

Deno.test('side counter: every item is an option, condiments attach', () => {
  assertEquals(
    parseAccompaniments('Pommes frites (3), Tomatennudeln (a.1,a), Kräuterpüree (3,g,l)', 'side'),
    { sides: [], alternatives: ['Pommes frites', 'Tomatennudeln', 'Kräuterpüree'] },
  );
  assertEquals(
    parseAccompaniments('Country frites (3), Curryreis, Nudeln Penne Rigate (a.1,a), Kräuterpesto (4)', 'side'),
    { sides: [], alternatives: ['Country frites', 'Curryreis', 'Nudeln Penne Rigate mit Kräuterpesto'] },
  );
  assertEquals(
    parseAccompaniments('Blattsalatmix, Frenchdressing (3,c,g,j)', 'salad'),
    { sides: [], alternatives: ['Blattsalatmix mit Frenchdressing'] },
  );
});

Deno.test('dessert: topping for the headline, "oder" separates options', () => {
  assertEquals(
    parseAccompaniments(
      'mit veganer Vanillesauce (3,f) oder Hausgemachter Fruchtquark Ananas (g) mit vegane Schokoraspeln (f) oder ' +
        'Vegane Götterspeise Waldmeister (2) mit veganer Schlagcreme oder Veganer Joghurt mit Himbeeren und veganen Oreos (a,a.3,a.1,f,h.1,h)',
      'dessert',
    ),
    {
      sides: ['veganer Vanillesauce'],
      alternatives: [
        'Hausgemachter Fruchtquark Ananas mit vegane Schokoraspeln',
        'Vegane Götterspeise Waldmeister mit veganer Schlagcreme',
        'Veganer Joghurt mit Himbeeren und veganen Oreos',
      ],
    },
  );
});

Deno.test('dessert: "und" adds a topping or, before a dessert noun, a new option', () => {
  assertEquals(
    parseAccompaniments(
      'mit veganer Vanillesauce (3,f) oder Hausgemachter Fruchtquark Heidelbeer (g) mit Brombeeren, und, weiße Schokoraspeln (g) oder Vanillejoghurt (g) mit Kirschsauce',
      'dessert',
    ).alternatives,
    ['Hausgemachter Fruchtquark Heidelbeer mit Brombeeren und weiße Schokoraspeln', 'Vanillejoghurt mit Kirschsauce'],
  );
  assertEquals(
    parseAccompaniments(
      'mit frischen Johannisbeeren oder Hausgemachter Fruchtquark Aprikose (g) mit gehackten Pistazien (h.7,h), und, Kokos-Joghurt Dessert (g) mit Schokoladensauce',
      'dessert',
    ).alternatives,
    ['Hausgemachter Fruchtquark Aprikose mit gehackten Pistazien', 'Kokos-Joghurt Dessert mit Schokoladensauce'],
  );
});

Deno.test('dessert: English variant with comma-separated connectors', () => {
  assertEquals(
    parseAccompaniments(
      'or, vegan chocolat pudding (a.3,a), with, vegan vanilla sauce (3,f), flaked almonds (h.1,h), or, homemade fruit quark with mixed berries (g), with, chocolate rolls (g)',
      'dessert',
      'en',
    ),
    {
      sides: [],
      alternatives: [
        'vegan chocolat pudding with vegan vanilla sauce, flaked almonds',
        'homemade fruit quark with mixed berries with chocolate rolls',
      ],
    },
  );
});

Deno.test('dessert without "oder" is just toppings', () => {
  assertEquals(parseAccompaniments('mit Kirschtopping', 'dessert'), { sides: ['Kirschtopping'], alternatives: [] });
});

Deno.test('empty input', () => {
  assertEquals(parseAccompaniments('', 'main'), { sides: [], alternatives: [] });
  assertEquals(parseAccompaniments(null, 'side'), { sides: [], alternatives: [] });
});

Deno.test('isRealSide keeps sides, drops sauces, dips, garnish, bread and extras', () => {
  for (const side of [
    'Butterkartoffeln', 'Pestokartoffeln', 'Senf-Kartoffeln', 'Pommes frites', 'Spätzle', 'Naturreis',
    'Koriander Wildreismischung', 'Fingermöhren', 'Rosenkohlröschen mit Speck', 'Kartoffelsalat mit Mayonnaise',
    'Blattsalatmix mit Frenchdressing', 'Gurkensalat in Sauerrahm', 'Green Fusion Slaw',
  ]) assertEquals(isRealSide(side), true, side);
  for (const notSide of [
    'Remouladensauce', 'vegane Remouladensauce', 'Mediterrane Gemüsesauce', 'Tomatensauce Parmerosa',
    'Kräuterquark Dip', 'Hausgemachter Kräuterquark', 'Mango-Curryketchup', 'Pfeffer Hollandaise', 'Portion Senf',
    'Zitronenecke', 'frische Petersilie', 'Frühlingszwiebelröllchen', 'Tagesdessert', 'Ciabattabrot',
    'zusätzlich Geflügelwürstchen Wiener Art', 'Ziegenkäse-Honig-Sauce oder fruchtige Tomaten-Basilikum-Sugo',
  ]) assertEquals(isRealSide(notSide), false, notSide);
});

Deno.test('photoItemsOf: counter options, real sides of a main, dessert options', () => {
  assertEquals(
    photoItemsOf({ course: 'side', clean_title: 'Senf-Kartoffeln', title: 'Senf-Kartoffeln (j)', sides: [], alternatives: ['Pommes frites'] }),
    [{ name: 'Senf-Kartoffeln', course: 'side' }, { name: 'Pommes frites', course: 'side' }],
  );
  assertEquals(
    photoItemsOf({ course: 'main', clean_title: 'Kibbelinge', title: 'Kibbelinge', sides: ['Remouladensauce', 'Zitronenecke', 'Butterkartoffeln', 'Tomatensalat'], alternatives: [] }),
    [{ name: 'Butterkartoffeln', course: 'side' }, { name: 'Tomatensalat', course: 'salad' }],
  );
  // Named like the app's counterOptions(…, De): "<title> mit <topping>", then the others.
  assertEquals(
    photoItemsOf({ course: 'dessert', clean_title: 'Rote Grütze', title: 'x', sides: ['veganer Vanillesauce'], alternatives: ['Fruchtquark Ananas'] }),
    [{ name: 'Rote Grütze mit veganer Vanillesauce', course: 'dessert' }, { name: 'Fruchtquark Ananas', course: 'dessert' }],
  );
  assertEquals(sideImageFile('Senf-Kartoffeln'), 'senf_kartoffeln.jpg');
  // The Pastabuffet's pastas and sauces are buffet items, not sides.
  assertEquals(
    photoItemsOf({ course: 'main', clean_title: 'Pastabuffet', title: 'Pastabuffet', sides: ['Spaghetti', 'Spinatnudeln'], alternatives: [] }),
    [],
  );
});
