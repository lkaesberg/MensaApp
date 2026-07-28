-- One-time cleanup: switch the app to generic-only meal images.
--
-- Background: the `rapid-processor` edge function generated a unique photo per
-- meal row at `<id>.png` (bucket root) and wrote that path into both
-- `image_path` and — for same-title rows — `image_path_generic`. The client
-- prefers `image_path` and rewrites `.png` -> `.jpg` when building the storage
-- URL, so those `.png` rows resolve to a non-existent `.jpg` and render broken.
-- rapid-processor is no longer scheduled; we want generic images only
-- (smooth-endpoint -> `generic/<title>.jpg`).
--
-- Idempotent and a no-op on a fresh database. Safe to replay.

-- 1) Forget the broken per-meal PNGs; the app falls back to the generic image.
UPDATE public.meals
SET image_path = NULL
WHERE image_path IS NOT NULL;

-- 2) Drop generic references that point at root-level PNGs (rapid-processor's
--    writes), keeping the real `generic/<title>.jpg`. The inherit/propagate
--    triggers + smooth-endpoint refill these with proper JPEGs over the next
--    few hourly runs.
UPDATE public.meals
SET image_path_generic = NULL
WHERE image_path_generic IS NOT NULL
  AND image_path_generic NOT LIKE 'generic/%';
