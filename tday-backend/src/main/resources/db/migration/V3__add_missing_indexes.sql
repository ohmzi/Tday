CREATE INDEX IF NOT EXISTS note_userid ON public.note USING btree ("userID");

CREATE INDEX IF NOT EXISTS file_userid ON public.file USING btree ("userID");

CREATE INDEX IF NOT EXISTS account_userid ON public.account USING btree ("userId");
