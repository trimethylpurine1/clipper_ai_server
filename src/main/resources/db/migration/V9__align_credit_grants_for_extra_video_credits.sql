ALTER TABLE credit_grants
ALTER COLUMN grant_type DROP NOT NULL;

ALTER TABLE credit_grants
ALTER COLUMN status SET DEFAULT 'PENDING_PAYMENT';

UPDATE credit_grants
SET status = 'PENDING_PAYMENT'
WHERE status IN ('pending', 'pending_payment');

UPDATE credit_grants
SET status = 'PAYMENT_FAILED'
WHERE status = 'payment_failed';

UPDATE credit_grants
SET credit_type = 'VIDEO_UPLOAD'
WHERE credit_type = 'video_upload';

UPDATE one_time_credit_products
SET credit_type = 'VIDEO_UPLOAD'
WHERE credit_type = 'video_upload';