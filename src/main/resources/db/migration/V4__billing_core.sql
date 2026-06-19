-- V3__billing_core.sql
-- Or use the next Flyway version number in your project.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================================================
-- 1. Billing customers
-- Separate Stripe Customer identity from app_users.
-- =========================================================

CREATE TABLE IF NOT EXISTS billing_customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id VARCHAR(128) NOT NULL UNIQUE,
    stripe_customer_id VARCHAR(255) NOT NULL UNIQUE,

    email VARCHAR(320),

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_billing_customers_user
        FOREIGN KEY (user_id)
        REFERENCES app_users(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_billing_customers_user_id
ON billing_customers(user_id);

CREATE INDEX IF NOT EXISTS idx_billing_customers_stripe_customer_id
ON billing_customers(stripe_customer_id);


-- =========================================================
-- 2. Move old app_users.stripe_customer_id into billing_customers,
-- then remove stripe_customer_id from app_users.
-- This is safe even if no users have Stripe customers yet.
-- =========================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'app_users'
          AND column_name = 'stripe_customer_id'
    ) THEN

        INSERT INTO billing_customers (
            user_id,
            stripe_customer_id,
            email,
            created_at,
            updated_at
        )
        SELECT
            id,
            stripe_customer_id,
            email,
            NOW(),
            NOW()
        FROM app_users
        WHERE stripe_customer_id IS NOT NULL
          AND stripe_customer_id <> ''
        ON CONFLICT DO NOTHING;

        ALTER TABLE app_users
        DROP COLUMN stripe_customer_id;
    END IF;
END $$;


-- =========================================================
-- 3. Subscription plans additions
-- You already have subscription_plans, so this only adds
-- missing Stripe/product billing fields.
-- =========================================================

ALTER TABLE subscription_plans
ADD COLUMN IF NOT EXISTS stripe_product_id VARCHAR(255);

ALTER TABLE subscription_plans
ADD COLUMN IF NOT EXISTS currency VARCHAR(10) NOT NULL DEFAULT 'usd';

-- Avoid naming a DB column "interval"; use billing_interval instead.
ALTER TABLE subscription_plans
ADD COLUMN IF NOT EXISTS billing_interval VARCHAR(50) NOT NULL DEFAULT 'month';

CREATE INDEX IF NOT EXISTS idx_subscription_plans_stripe_price_id
ON subscription_plans(stripe_price_id);

CREATE INDEX IF NOT EXISTS idx_subscription_plans_active
ON subscription_plans(is_active);


-- =========================================================
-- 4. User subscriptions additions
-- Adds billing_customer_id and extra lifecycle fields.
-- =========================================================

ALTER TABLE user_subscriptions
ADD COLUMN IF NOT EXISTS billing_customer_id UUID;

ALTER TABLE user_subscriptions
ADD COLUMN IF NOT EXISTS trial_end TIMESTAMPTZ;

ALTER TABLE user_subscriptions
ADD COLUMN IF NOT EXISTS ended_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_user_subscriptions_billing_customer'
    ) THEN
        ALTER TABLE user_subscriptions
        ADD CONSTRAINT fk_user_subscriptions_billing_customer
            FOREIGN KEY (billing_customer_id)
            REFERENCES billing_customers(id)
            ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_user_subscriptions_user_id
ON user_subscriptions(user_id);

CREATE INDEX IF NOT EXISTS idx_user_subscriptions_billing_customer_id
ON user_subscriptions(billing_customer_id);

CREATE INDEX IF NOT EXISTS idx_user_subscriptions_stripe_subscription_id
ON user_subscriptions(stripe_subscription_id);

CREATE INDEX IF NOT EXISTS idx_user_subscriptions_status
ON user_subscriptions(status);

CREATE INDEX IF NOT EXISTS idx_user_subscriptions_current_period_end
ON user_subscriptions(current_period_end);


-- =========================================================
-- 5. Credit grants
-- Supports one-time extra video/upload purchases.
-- =========================================================

CREATE TABLE IF NOT EXISTS credit_grants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id VARCHAR(128) NOT NULL,
    usage_period_id UUID,

    grant_type VARCHAR(50) NOT NULL,

    source_videos_granted INTEGER NOT NULL DEFAULT 0,
    source_videos_used INTEGER NOT NULL DEFAULT 0,

    amount_paid_cents INTEGER,

    stripe_payment_intent_id VARCHAR(255) UNIQUE,
    stripe_invoice_id VARCHAR(255),

    status VARCHAR(50) NOT NULL DEFAULT 'pending',

    expires_at TIMESTAMPTZ,
    refunded_at TIMESTAMPTZ,
    stripe_refund_id VARCHAR(255),

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_credit_grants_user
        FOREIGN KEY (user_id)
        REFERENCES app_users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_credit_grants_usage_period
        FOREIGN KEY (usage_period_id)
        REFERENCES usage_periods(id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_credit_grants_user_id
ON credit_grants(user_id);

CREATE INDEX IF NOT EXISTS idx_credit_grants_usage_period_id
ON credit_grants(usage_period_id);

CREATE INDEX IF NOT EXISTS idx_credit_grants_status
ON credit_grants(status);

CREATE INDEX IF NOT EXISTS idx_credit_grants_stripe_payment_intent_id
ON credit_grants(stripe_payment_intent_id);


-- =========================================================
-- 6. Billing invoices
-- Local mirror of Stripe invoice/payment records.
-- =========================================================

CREATE TABLE IF NOT EXISTS billing_invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id VARCHAR(128) NOT NULL,
    subscription_id UUID,

    stripe_invoice_id VARCHAR(255) UNIQUE,
    stripe_payment_intent_id VARCHAR(255),
    stripe_charge_id VARCHAR(255),

    amount_paid_cents INTEGER NOT NULL DEFAULT 0,
    amount_refunded_cents INTEGER NOT NULL DEFAULT 0,

    currency VARCHAR(10) NOT NULL DEFAULT 'usd',

    status VARCHAR(50) NOT NULL,
    reason VARCHAR(50) NOT NULL,

    commissionable BOOLEAN NOT NULL DEFAULT FALSE,
    commission_basis_cents INTEGER NOT NULL DEFAULT 0,

    period_start TIMESTAMPTZ,
    period_end TIMESTAMPTZ,

    paid_at TIMESTAMPTZ,
    refunded_at TIMESTAMPTZ,
    refund_status VARCHAR(50) NOT NULL DEFAULT 'none',

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_billing_invoices_user
        FOREIGN KEY (user_id)
        REFERENCES app_users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_billing_invoices_subscription
        FOREIGN KEY (subscription_id)
        REFERENCES user_subscriptions(id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_billing_invoices_user_id
ON billing_invoices(user_id);

CREATE INDEX IF NOT EXISTS idx_billing_invoices_subscription_id
ON billing_invoices(subscription_id);

CREATE INDEX IF NOT EXISTS idx_billing_invoices_stripe_invoice_id
ON billing_invoices(stripe_invoice_id);

CREATE INDEX IF NOT EXISTS idx_billing_invoices_stripe_payment_intent_id
ON billing_invoices(stripe_payment_intent_id);

CREATE INDEX IF NOT EXISTS idx_billing_invoices_status
ON billing_invoices(status);

CREATE INDEX IF NOT EXISTS idx_billing_invoices_reason
ON billing_invoices(reason);


-- =========================================================
-- 7. Billing refunds
-- Local mirror of Stripe refunds.
-- =========================================================

CREATE TABLE IF NOT EXISTS billing_refunds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id VARCHAR(128) NOT NULL,
    billing_invoice_id UUID,

    stripe_refund_id VARCHAR(255) NOT NULL UNIQUE,
    stripe_charge_id VARCHAR(255),
    stripe_payment_intent_id VARCHAR(255),

    amount_refunded_cents INTEGER NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'usd',

    status VARCHAR(50) NOT NULL,
    reason VARCHAR(50) NOT NULL,
    refund_type VARCHAR(50) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,

    CONSTRAINT fk_billing_refunds_user
        FOREIGN KEY (user_id)
        REFERENCES app_users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_billing_refunds_invoice
        FOREIGN KEY (billing_invoice_id)
        REFERENCES billing_invoices(id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_billing_refunds_user_id
ON billing_refunds(user_id);

CREATE INDEX IF NOT EXISTS idx_billing_refunds_billing_invoice_id
ON billing_refunds(billing_invoice_id);

CREATE INDEX IF NOT EXISTS idx_billing_refunds_stripe_refund_id
ON billing_refunds(stripe_refund_id);

CREATE INDEX IF NOT EXISTS idx_billing_refunds_status
ON billing_refunds(status);


-- =========================================================
-- 8. Stripe webhook events
-- Dedupe + processing status for Stripe webhooks.
-- This is not the same as audit_logs.
-- =========================================================

CREATE TABLE IF NOT EXISTS stripe_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    stripe_event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    stripe_object_id VARCHAR(255),

    status VARCHAR(50) NOT NULL DEFAULT 'received',
    error_message TEXT,

    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_stripe_webhook_events_stripe_event_id
ON stripe_webhook_events(stripe_event_id);

CREATE INDEX IF NOT EXISTS idx_stripe_webhook_events_event_type
ON stripe_webhook_events(event_type);

CREATE INDEX IF NOT EXISTS idx_stripe_webhook_events_status
ON stripe_webhook_events(status);

CREATE INDEX IF NOT EXISTS idx_stripe_webhook_events_received_at
ON stripe_webhook_events(received_at DESC);


-- =========================================================
-- 9. Commission updates
-- Lets commissions connect to billing invoices and be reversed.
-- =========================================================

ALTER TABLE commissions
ADD COLUMN IF NOT EXISTS billing_invoice_id UUID;

ALTER TABLE commissions
ADD COLUMN IF NOT EXISTS reversed_at TIMESTAMPTZ;

ALTER TABLE commissions
ADD COLUMN IF NOT EXISTS reversal_reason VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_commissions_billing_invoice'
    ) THEN
        ALTER TABLE commissions
        ADD CONSTRAINT fk_commissions_billing_invoice
            FOREIGN KEY (billing_invoice_id)
            REFERENCES billing_invoices(id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- If one invoice should only ever create one commission, keep this unique index.
CREATE UNIQUE INDEX IF NOT EXISTS uq_commissions_billing_invoice_id
ON commissions(billing_invoice_id)
WHERE billing_invoice_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_commissions_status
ON commissions(status);

CREATE INDEX IF NOT EXISTS idx_commissions_available_after
ON commissions(available_after);


-- =========================================================
-- 10. Audit indexes
-- Makes audit lookup/debugging easier.
-- =========================================================

CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_user_id
ON audit_logs(actor_user_id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_action
ON audit_logs(action);

CREATE INDEX IF NOT EXISTS idx_audit_logs_target
ON audit_logs(target_type, target_id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_job_id
ON audit_logs(job_id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at
ON audit_logs(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_request_id
ON audit_logs(request_id);