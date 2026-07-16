-- Deadlines are minute-precision app-wide (drop seconds & fractional seconds). New writes are
-- floored by parseDueMinute in the routes; this one-time pass floors existing rows so stored
-- dues, recurring-occurrence keys, and ICS EXDATE/RECURRENCE-ID stay aligned.
--
-- Real physical table names are unquoted lowercase (todos, todo_instances, completedtodo) even
-- though Exposed maps Table("CompletedTodo"); camelCase COLUMNS are quoted. date_trunc('minute')
-- drops seconds and microseconds. Only due/instanceDate/overriddenDue/exdates are floored here —
-- createdAt/updatedAt (LWW sync clock) and completedAt (audit) intentionally keep full precision.

UPDATE todos SET due = date_trunc('minute', due);

UPDATE todos
SET exdates = ARRAY(SELECT date_trunc('minute', d) FROM unnest(exdates) AS d)
WHERE array_length(exdates, 1) > 0;

UPDATE todo_instances SET "instanceDate" = date_trunc('minute', "instanceDate");

UPDATE todo_instances
SET "overriddenDue" = date_trunc('minute', "overriddenDue")
WHERE "overriddenDue" IS NOT NULL;

UPDATE completedtodo SET due = date_trunc('minute', due);

UPDATE completedtodo
SET "instanceDate" = date_trunc('minute', "instanceDate")
WHERE "instanceDate" IS NOT NULL;
