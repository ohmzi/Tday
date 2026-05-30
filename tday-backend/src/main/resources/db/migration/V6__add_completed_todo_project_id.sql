ALTER TABLE completedtodo
    ADD COLUMN IF NOT EXISTS "projectID" character varying(30);

UPDATE completedtodo completed
SET "projectID" = todo."projectID"
FROM todos todo
WHERE completed."projectID" IS NULL
  AND todo."projectID" IS NOT NULL
  AND completed."originalTodoID" = todo.id
  AND completed."userID" = todo."userID";

CREATE INDEX IF NOT EXISTS completedtodo_projectid
    ON completedtodo USING btree ("projectID");

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_completedtodo_projectid__id'
    ) THEN
        ALTER TABLE completedtodo
            ADD CONSTRAINT fk_completedtodo_projectid__id
            FOREIGN KEY ("projectID")
            REFERENCES project(id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT;
    END IF;
END $$;
