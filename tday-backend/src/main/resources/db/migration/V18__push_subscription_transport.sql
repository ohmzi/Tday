-- Push transport per subscription.
--
-- Until now every push_subscriptions row was a browser Web Push endpoint (VAPID,
-- p256dh/auth encrypted). UnifiedPush lets Android self-hosters receive server pushes
-- through their own distributor (e.g. ntfy): the server just POSTs the payload to the
-- endpoint URL, with no VAPID keys. A transport column distinguishes the two so
-- sendToUser can fan out correctly. Existing rows are Web Push.
ALTER TABLE push_subscriptions
    ADD COLUMN IF NOT EXISTS transport VARCHAR(20) NOT NULL DEFAULT 'webpush';
