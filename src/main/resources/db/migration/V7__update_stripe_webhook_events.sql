ALTER TABLE stripe_webhook_events
DROP CONSTRAINT IF EXISTS stripe_webhook_events_status_check;

ALTER TABLE stripe_webhook_events
ADD CONSTRAINT stripe_webhook_events_status_check
CHECK (
    status IN (
        'received',
        'processed',
        'failed'
    )
);