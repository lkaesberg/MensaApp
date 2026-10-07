-- Menu structure: course, alternatives and counter order.
--
-- The upstream API flattens every counter into one shape, so a Mensa am Turm
-- "Stärkebeilage" (Senf-Kartoffeln + "Pommes frites, Tomatennudeln, …") looked
-- like a main dish with sides, and desserts carried their alternatives as one
-- "mit … oder … oder …" side. mensa-menu-sync now classifies each counter and
-- parses <essen2> accordingly (supabase/functions/_shared/menu.ts).

ALTER TABLE public.meals
  ADD COLUMN IF NOT EXISTS course text,
  ADD COLUMN IF NOT EXISTS alternatives text[],
  ADD COLUMN IF NOT EXISTS alternatives_en text[];

COMMENT ON COLUMN public.meals.course IS
  'main | soup | side | salad | dessert — from the counter category. NULL on rows synced before 2026-10-08.';
COMMENT ON COLUMN public.meals.alternatives IS
  'Other options at the same counter (side counters, desserts, buffet pairs). sides = served with this dish.';

-- <preis_pos>: the canteen's own counter order (Turm: 130 Turm Vegan … 180
-- Gemüsebeilage … 200 Asia Point … 210 Dessert). The app groups by course
-- and sorts within each group by this.
ALTER TABLE public.meal_dates
  ADD COLUMN IF NOT EXISTS sort_order integer;

-- Inherit an existing photo across allergen-code drift on insert, not just
-- exact-title matches. "Senf-Kartoffeln (j)" and "Senf-Kartoffeln (3,j)" are
-- the same dish; without this each code variant waited for its own generated
-- image. meal_title_core() comes from 20260730_refresh_generic_image_on_rename.
CREATE OR REPLACE FUNCTION public.inherit_generic_image() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.image_path_generic IS NULL AND NEW.title IS NOT NULL THEN
    SELECT image_path_generic
      INTO NEW.image_path_generic
    FROM public.meals
    WHERE title = NEW.title
      AND image_path_generic IS NOT NULL
    LIMIT 1;

    IF NEW.image_path_generic IS NULL THEN
      SELECT image_path_generic
        INTO NEW.image_path_generic
      FROM public.meals
      WHERE public.meal_title_core(title) = public.meal_title_core(NEW.title)
        AND image_path_generic IS NOT NULL
      LIMIT 1;
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

-- On closed days the API sends one "Heute leider geschlossen" entry, which
-- the sync used to store as a dish (with a generated photo). The sync now
-- skips it; retire the rows already in the table so they leave the feed and
-- the history/stats screens. Idempotent.
UPDATE public.meal_dates AS md
SET deactivated_at = now()
FROM public.meals AS m
WHERE md.meal_id = m.id
  AND md.deactivated_at IS NULL
  AND m.title ~* '^\s*(heute\s+)?(leider\s+)?(geschlossen|closed)\M';
