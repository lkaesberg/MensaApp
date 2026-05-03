

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


CREATE EXTENSION IF NOT EXISTS "pg_cron" WITH SCHEMA "pg_catalog";






CREATE EXTENSION IF NOT EXISTS "pg_net" WITH SCHEMA "extensions";






COMMENT ON SCHEMA "public" IS 'standard public schema';



CREATE EXTENSION IF NOT EXISTS "pg_stat_statements" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "supabase_vault" WITH SCHEMA "vault";






CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA "extensions";





SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "public"."canteens" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."canteens" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."meal_dates" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "meal_id" "uuid",
    "canteen_id" "uuid" NOT NULL,
    "served_on" "date" NOT NULL,
    "category" "text" NOT NULL,
    "note" "text",
    "created_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."meal_dates" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."meals" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "title" "text" NOT NULL,
    "full_text" "text" NOT NULL,
    "icons" "text"[],
    "created_at" timestamp with time zone DEFAULT "now"(),
    "image_path" "text",
    "image_path_generic" "text"
);


ALTER TABLE "public"."meals" OWNER TO "postgres";


ALTER TABLE ONLY "public"."canteens"
    ADD CONSTRAINT "canteens_name_key" UNIQUE ("name");



ALTER TABLE ONLY "public"."canteens"
    ADD CONSTRAINT "canteens_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."meal_dates"
    ADD CONSTRAINT "meal_dates_meal_id_canteen_id_served_on_key" UNIQUE ("meal_id", "canteen_id", "served_on");



ALTER TABLE ONLY "public"."meal_dates"
    ADD CONSTRAINT "meal_dates_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."meal_dates"
    ADD CONSTRAINT "meal_dates_served_on_category_canteen_key" UNIQUE ("served_on", "category", "canteen_id");



ALTER TABLE ONLY "public"."meals"
    ADD CONSTRAINT "meals_full_text_key" UNIQUE ("full_text");



ALTER TABLE ONLY "public"."meals"
    ADD CONSTRAINT "meals_pkey" PRIMARY KEY ("id");



CREATE INDEX "meal_dates_served_on_idx" ON "public"."meal_dates" USING "btree" ("served_on");



CREATE INDEX "meals_image_null" ON "public"."meals" USING "btree" ("image_path");



ALTER TABLE ONLY "public"."meal_dates"
    ADD CONSTRAINT "meal_dates_canteen_id_fkey" FOREIGN KEY ("canteen_id") REFERENCES "public"."canteens"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."meal_dates"
    ADD CONSTRAINT "meal_dates_meal_id_fkey" FOREIGN KEY ("meal_id") REFERENCES "public"."meals"("id") ON DELETE CASCADE;



CREATE POLICY "Enable read access for all users" ON "public"."canteens" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "public"."meal_dates" FOR SELECT USING (true);



CREATE POLICY "Enable read access for all users" ON "public"."meals" FOR SELECT USING (true);



ALTER TABLE "public"."canteens" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."meal_dates" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."meals" ENABLE ROW LEVEL SECURITY;




ALTER PUBLICATION "supabase_realtime" OWNER TO "postgres";








GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";
































































































































































































GRANT ALL ON TABLE "public"."canteens" TO "anon";
GRANT ALL ON TABLE "public"."canteens" TO "authenticated";
GRANT ALL ON TABLE "public"."canteens" TO "service_role";



GRANT ALL ON TABLE "public"."meal_dates" TO "anon";
GRANT ALL ON TABLE "public"."meal_dates" TO "authenticated";
GRANT ALL ON TABLE "public"."meal_dates" TO "service_role";



GRANT ALL ON TABLE "public"."meals" TO "anon";
GRANT ALL ON TABLE "public"."meals" TO "authenticated";
GRANT ALL ON TABLE "public"."meals" TO "service_role";









ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES  TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES  TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS  TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS  TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES  TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES  TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES  TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES  TO "service_role";






























