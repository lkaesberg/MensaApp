-- Re-resolve a meal's generic image when its title changes.
--
-- `meals` is upserted ON CONFLICT (external_id) — the upstream <speise id>.
-- When the Studierendenwerk reuses a speise id for a different dish (routinely,
-- when a counter's offering changes), the upsert overwrites `title` but leaves
-- `image_path_generic` pointing at the *previous* dish's photo. smooth-endpoint
-- only ever selects rows WHERE image_path_generic IS NULL, so it never revisits
-- them and the wrong photo sticks permanently.
--
-- Observed on 2026-07-29: 494 of 9557 meals showed another dish's image —
-- "Blumenkohlgemüse" rendering erbsen_karottengemüse, "Pommes frites"
-- rendering gnocchi_kartoffelnudeln, and so on.
--
-- Contract after this migration: a title change re-resolves the image —
-- adopt the existing generic photo for the new title if one has already been
-- generated, otherwise NULL so smooth-endpoint generates a fresh one.

-- Mirrors smooth-endpoint's path derivation:
--   title.replace(/\W+/g, '_').toLowerCase()
-- JS \W without the /u flag is ASCII-only, so "ü" is a non-word char there.
-- The character class below must stay ASCII to match — do not "fix" it to
-- include accented letters or every existing path stops matching.
CREATE OR REPLACE FUNCTION public.generic_image_slug(t text) RETURNS text
LANGUAGE sql IMMUTABLE STRICT
AS $$
  SELECT lower(regexp_replace(t, '[^A-Za-z0-9_]+', '_', 'g'))
$$;

-- Mirrors stripAllergenParens() in _shared/text.ts. `title` carries the raw
-- <essen> text *including* allergen codes, and those drift day to day for an
-- unchanged dish ("… (3,a.1,a)" → "… (3,a.1,a,i)"). Matching on the stripped
-- form keeps a code change from being mistaken for a new dish and needlessly
-- regenerating an image. Derived from `title` rather than the `clean_title`
-- column because clean_title is NULL on 5612 of 9557 rows (pre-2026-05).
CREATE OR REPLACE FUNCTION public.meal_title_core(t text) RETURNS text
LANGUAGE sql IMMUTABLE STRICT
AS $$
  SELECT btrim(regexp_replace(regexp_replace(t, '\s*\([^)]*\)', '', 'g'), '\s+', ' ', 'g'))
$$;

CREATE OR REPLACE FUNCTION public.reresolve_generic_image() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  -- 1. Exact title match — the same dish already has a photo.
  SELECT image_path_generic
    INTO NEW.image_path_generic
  FROM public.meals
  WHERE title = NEW.title
    AND id <> NEW.id
    AND image_path_generic IS NOT NULL
  LIMIT 1;

  -- 2. Same dish, drifted allergen codes.
  IF NEW.image_path_generic IS NULL THEN
    SELECT image_path_generic
      INTO NEW.image_path_generic
    FROM public.meals
    WHERE public.meal_title_core(title) = public.meal_title_core(NEW.title)
      AND id <> NEW.id
      AND image_path_generic IS NOT NULL
    LIMIT 1;
  END IF;

  -- 3. Neither matched → genuinely new dish. NEW.image_path_generic is NULL,
  --    so smooth-endpoint generates a fresh photo on its next run.
  RETURN NEW;
END;
$$;

-- BEFORE UPDATE OF title only. smooth-endpoint's own writes touch
-- image_path_generic without touching title, so they don't trip this; and
-- propagate_generic_image's sibling updates likewise leave title alone, so
-- there's no recursion between the two triggers.
DROP TRIGGER IF EXISTS meals_reresolve_generic_image ON public.meals;
CREATE TRIGGER meals_reresolve_generic_image
BEFORE UPDATE OF title ON public.meals
FOR EACH ROW
WHEN (NEW.title IS DISTINCT FROM OLD.title)
EXECUTE FUNCTION public.reresolve_generic_image();

-- One-time repair of rows that already drifted. A path is correct when it is
-- generic/<slug(title)>.(jpg|png) — the .png variants are pre-2026-06 uploads
-- that the client rewrites to .jpg, so they're fine and must not be discarded.
--
-- For each mismatched row: adopt the correct generic path if any row already
-- points at it (the image exists in storage), else NULL to force regeneration.
-- Idempotent — re-running matches nothing once clean.
UPDATE public.meals AS m
SET image_path_generic = COALESCE(
  -- exact title
  (SELECT s.image_path_generic
     FROM public.meals AS s
    WHERE s.image_path_generic IN (
        'generic/' || public.generic_image_slug(m.title) || '.jpg',
        'generic/' || public.generic_image_slug(m.title) || '.png')
    LIMIT 1),
  -- same dish, drifted allergen codes
  (SELECT s.image_path_generic
     FROM public.meals AS s
    WHERE public.meal_title_core(s.title) = public.meal_title_core(m.title)
      AND s.image_path_generic IN (
          'generic/' || public.generic_image_slug(s.title) || '.jpg',
          'generic/' || public.generic_image_slug(s.title) || '.png')
    LIMIT 1)
)
WHERE m.image_path_generic IS NOT NULL
  AND m.title IS NOT NULL
  AND m.image_path_generic <> 'generic/' || public.generic_image_slug(m.title) || '.jpg'
  AND m.image_path_generic <> 'generic/' || public.generic_image_slug(m.title) || '.png';
