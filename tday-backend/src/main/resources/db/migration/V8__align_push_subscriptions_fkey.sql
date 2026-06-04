DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'push_subscriptions_userID_fkey'
    ) THEN
        ALTER TABLE push_subscriptions
            RENAME CONSTRAINT "push_subscriptions_userID_fkey" TO push_subscriptions_userid_fkey;
    END IF;
END $$;
