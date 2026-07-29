-- Merge the case-variant "CGIN" canteen back into "CGiN".
--
-- On 2026-07-24 the menu API started emitting the Friday burger counters
-- under a second <mensa name="CGIN"> block instead of the regular "CGiN"
-- one. `canteens.name` is UNIQUE but case-sensitive, so getOrCreateCanteenId
-- forked a new canteen: no slug, no external_id, no opening hours, no
-- occupancy — and a duplicate entry in the app's mensa picker, with the
-- Friday burgers orphaned off the real CGiN feed.
--
-- The recurrence is fixed in _shared/supabase.ts (canteen names are now
-- folded case-insensitively before lookup). This migration repairs the rows
-- that already forked.
--
-- Idempotent: a no-op once the stray canteen is gone.

do $$
declare
  canonical_id uuid;
  stray_id     uuid;
  moved        int;
begin
  select id into canonical_id from public.canteens where name = 'CGiN';
  select id into stray_id     from public.canteens where name = 'CGIN';

  if canonical_id is null or stray_id is null then
    raise notice 'merge_cgin: nothing to do (canonical=% stray=%)', canonical_id, stray_id;
    return;
  end if;

  -- Repoint menu rows. The (canteen_id, served_on, category) UNIQUE currently
  -- can't collide — the stray's categories ("CGiN Basic Burger", "CGiN Tasty
  -- Burger vegan") don't exist on the canonical row, and its dates start the
  -- Friday after the canonical row's last burger entry. The NOT EXISTS guard
  -- keeps that an assumption rather than a hard dependency.
  update public.meal_dates md
     set canteen_id = canonical_id
   where md.canteen_id = stray_id
     and not exists (
       select 1
         from public.meal_dates dup
        where dup.canteen_id = canonical_id
          and dup.served_on  = md.served_on
          and dup.category   = md.category
     );
  get diagnostics moved = row_count;

  -- Anything left on the stray row is a true duplicate of a canonical row.
  delete from public.meal_dates where canteen_id = stray_id;

  -- Empty today, but a stray row here would block the canteen delete.
  delete from public.canteen_hours     where canteen_id = stray_id;
  delete from public.canteen_prices    where canteen_id = stray_id;
  delete from public.canteen_occupancy where canteen_id = stray_id;

  delete from public.canteens where id = stray_id;

  raise notice 'merge_cgin: moved % meal_dates, dropped stray canteen %', moved, stray_id;
end $$;
