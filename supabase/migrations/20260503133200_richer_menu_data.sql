-- Richer menu data extracted from the studierendenwerk-goettingen.de cache pages.
-- All new columns are nullable so existing rows remain valid; the scraper
-- backfills them on its next run via upsert.

ALTER TABLE public.meals
  ADD COLUMN IF NOT EXISTS clean_title text,
  ADD COLUMN IF NOT EXISTS description text,
  ADD COLUMN IF NOT EXISTS sides text[],
  ADD COLUMN IF NOT EXISTS allergens text[],
  ADD COLUMN IF NOT EXISTS additives text[];

ALTER TABLE public.meal_dates
  ADD COLUMN IF NOT EXISTS meal_period text,
  ADD COLUMN IF NOT EXISTS deactivated_at timestamptz;

CREATE INDEX IF NOT EXISTS meal_dates_active_day_idx
  ON public.meal_dates (served_on)
  WHERE deactivated_at IS NULL;

-- Per-canteen price list scraped from /campusgastronomie/mensen/<slug> pages.
-- One row per price line (e.g. "Menü", "Vegetarisch/vegan", "CampusCurry").
-- Only Zentralmensa, Mensa am Turm, and CGiN publish prices today.
CREATE TABLE IF NOT EXISTS public.canteen_prices (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  canteen_id uuid NOT NULL REFERENCES public.canteens(id) ON DELETE CASCADE,
  category text NOT NULL,
  price_students text,
  price_employees text,
  price_guests text,
  price_students_cents int,
  price_employees_cents int,
  price_guests_cents int,
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (canteen_id, category)
);

ALTER TABLE public.canteen_prices ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Enable read access for all users"
  ON public.canteen_prices FOR SELECT USING (true);
