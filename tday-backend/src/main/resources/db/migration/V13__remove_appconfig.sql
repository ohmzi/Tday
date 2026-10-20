-- The global admin AI-summary toggle has been replaced by a per-user preference
-- (UserPreferences."aiSummaryEnabled") plus a backend-reported capability derived
-- from the Ollama deployment. The appconfig table is no longer used.
DROP TABLE IF EXISTS public.appconfig CASCADE;

-- Per-user AI-summary opt-out. NULL is treated as enabled (default ON) by the app.
ALTER TABLE public.userpreferences
    ADD COLUMN IF NOT EXISTS "aiSummaryEnabled" boolean;
