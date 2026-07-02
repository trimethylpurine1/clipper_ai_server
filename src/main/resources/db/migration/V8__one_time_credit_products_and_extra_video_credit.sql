CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE one_time_credit_products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,

    stripe_product_id VARCHAR(255),
    stripe_price_id VARCHAR(255) UNIQUE,

    unit_amount_cents INTEGER NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'usd',

    credit_type VARCHAR(50) NOT NULL,
    credit_quantity INTEGER NOT NULL,

    is_active BOOLEAN NOT NULL DEFAULT true,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT one_time_credit_products_unit_amount_positive
        CHECK (unit_amount_cents > 0),

    CONSTRAINT one_time_credit_products_credit_quantity_positive
        CHECK (credit_quantity > 0)
);

CREATE INDEX idx_one_time_credit_products_code_active
    ON one_time_credit_products (code, is_active);

CREATE INDEX idx_one_time_credit_products_stripe_price_id
    ON one_time_credit_products (stripe_price_id);
    
    ALTER TABLE credit_grants
    ADD COLUMN IF NOT EXISTS billing_customer_id UUID,
    ADD COLUMN IF NOT EXISTS one_time_credit_product_id UUID,
    ADD COLUMN IF NOT EXISTS billing_invoice_id UUID,
    ADD COLUMN IF NOT EXISTS stripe_payment_intent_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS credit_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS quantity INTEGER,
    ADD COLUMN IF NOT EXISTS remaining_quantity INTEGER,
    ADD COLUMN IF NOT EXISTS amount_cents INTEGER,
    ADD COLUMN IF NOT EXISTS currency VARCHAR(10),
    ADD COLUMN IF NOT EXISTS status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS reason VARCHAR(100),
    ADD COLUMN IF NOT EXISTS activated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS used_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS refunded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uq_credit_grants_stripe_payment_intent_id_not_null
    ON credit_grants (stripe_payment_intent_id)
    WHERE stripe_payment_intent_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_credit_grants_user_status
    ON credit_grants (user_id, status);

CREATE INDEX IF NOT EXISTS idx_credit_grants_billing_invoice_id
    ON credit_grants (billing_invoice_id);

CREATE INDEX IF NOT EXISTS idx_credit_grants_one_time_credit_product_id
    ON credit_grants (one_time_credit_product_id);
    
    
    ALTER TABLE credit_grants
    ADD CONSTRAINT fk_credit_grants_billing_customer
    FOREIGN KEY (billing_customer_id)
    REFERENCES billing_customers(id);

ALTER TABLE credit_grants
    ADD CONSTRAINT fk_credit_grants_one_time_credit_product
    FOREIGN KEY (one_time_credit_product_id)
    REFERENCES one_time_credit_products(id);

ALTER TABLE credit_grants
    ADD CONSTRAINT fk_credit_grants_billing_invoice
    FOREIGN KEY (billing_invoice_id)
    REFERENCES billing_invoices(id);
    
    