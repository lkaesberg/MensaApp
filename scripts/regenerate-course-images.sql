-- One-off: re-generate the photos of side counters, salads and desserts with
-- the course-aware prompts from 2026-10-08 (single bowl / dessert glass
-- instead of a full tray with invented companions).
--
-- Run in the Supabase SQL editor AFTER the 20261008_menu_structure migration
-- is deployed and mensa-menu-sync has run once (it fills meals.course).
-- Clears image_path_generic for every allergen-code variant of those dishes
-- that is on a plan in the next 14 days; smooth-endpoint regenerates them on
-- its hourly runs (8 per run by default). Until then the cards show the
-- plain placeholder. Cost: one gpt-image-1 "low" image per distinct dish.

-- 1. Preview: how many distinct dishes would be regenerated.
SELECT m.course, count(DISTINCT public.meal_title_core(m.title)) AS dishes
FROM public.meals m
JOIN public.meal_dates md ON md.meal_id = m.id
WHERE m.course IN ('side', 'salad', 'dessert')
  AND md.served_on BETWEEN current_date AND current_date + 14
  AND md.deactivated_at IS NULL
GROUP BY m.course;

-- 2. Clear them.
-- WITH targets AS (
--   SELECT DISTINCT public.meal_title_core(m.title) AS core
--   FROM public.meals m
--   JOIN public.meal_dates md ON md.meal_id = m.id
--   WHERE m.course IN ('side', 'salad', 'dessert')
--     AND md.served_on BETWEEN current_date AND current_date + 14
--     AND md.deactivated_at IS NULL
-- )
-- UPDATE public.meals AS m
-- SET image_path_generic = NULL
-- FROM targets AS t
-- WHERE public.meal_title_core(m.title) = t.core
--   AND m.image_path_generic IS NOT NULL;
