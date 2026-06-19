-- V1__create_initial_schema.sql

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================================================
-- Users
-- Firebase Auth owns authentication.
-- app_users.id should be the Firebase UID.
-- =========================================================

CREATE TABLE app_users (
    id VARCHAR(128) PRIMARY KEY, -- Firebase UID

    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),

    stripe_customer_id VARCHAR(255) UNIQUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- =========================================================
-- Subscription Plans / Billing
-- =========================================================

CREATE TABLE subscription_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name VARCHAR(100) NOT NULL UNIQUE,

    stripe_price_id VARCHAR(255) UNIQUE,

    monthly_price_cents INTEGER NOT NULL CHECK (monthly_price_cents >= 0),

    source_minutes_per_month INTEGER NOT NULL CHECK (source_minutes_per_month >= 0),
    rendered_clips_per_month INTEGER NOT NULL CHECK (rendered_clips_per_month >= 0),

    max_video_minutes INTEGER,
    max_file_size_mb INTEGER,

    allow_4k BOOLEAN NOT NULL DEFAULT false,

    is_active BOOLEAN NOT NULL DEFAULT true,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


CREATE TABLE user_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id VARCHAR(128) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    plan_id UUID REFERENCES subscription_plans(id) ON DELETE SET NULL,

    stripe_subscription_id VARCHAR(255) UNIQUE,
    stripe_status VARCHAR(100),

    status VARCHAR(50) NOT NULL CHECK (
        status IN (
            'trialing',
            'active',
            'past_due',
            'cancelled',
            'unpaid',
            'incomplete',
            'incomplete_expired'
        )
    ),

    current_period_start TIMESTAMPTZ,
    current_period_end TIMESTAMPTZ,

    cancel_at_period_end BOOLEAN NOT NULL DEFAULT false,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


CREATE TABLE usage_periods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id VARCHAR(128) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    subscription_id UUID REFERENCES user_subscriptions(id) ON DELETE SET NULL,

    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,

    source_seconds_used INTEGER NOT NULL DEFAULT 0 CHECK (source_seconds_used >= 0),
    rendered_clips_used INTEGER NOT NULL DEFAULT 0 CHECK (rendered_clips_used >= 0),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (user_id, period_start, period_end)
);


-- =========================================================
-- Campaigns
-- =========================================================

CREATE TABLE campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id VARCHAR(128) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,

    name VARCHAR(255) NOT NULL,

    creator_name VARCHAR(255),
    campaign_platform VARCHAR(100),

    raw_rules_text TEXT NOT NULL,

    payout_description TEXT,

    status VARCHAR(50) NOT NULL DEFAULT 'active' CHECK (
        status IN ('active', 'archived')
    ),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


CREATE TABLE campaign_rule_sets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,

    rules_json JSONB NOT NULL,

    model_name VARCHAR(255),
    model_version VARCHAR(255),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- =========================================================
-- Source Videos
-- =========================================================

CREATE TABLE source_videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id VARCHAR(128) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,

    original_filename VARCHAR(500) NOT NULL,

    storage_bucket VARCHAR(255),
    storage_key TEXT NOT NULL,

    file_size_bytes BIGINT CHECK (file_size_bytes >= 0),

    mime_type VARCHAR(255),

    duration_seconds NUMERIC(12, 3),
    width INTEGER,
    height INTEGER,
    frame_rate NUMERIC(10, 3),

    status VARCHAR(50) NOT NULL DEFAULT 'uploaded' CHECK (
        status IN (
            'uploaded',
            'queued',
            'processing',
            'processed',
            'failed',
            'deleted'
        )
    ),

    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- =========================================================
-- Video Processing Jobs
-- =========================================================

CREATE TABLE video_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id VARCHAR(128) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    source_video_id UUID NOT NULL REFERENCES source_videos(id) ON DELETE CASCADE,
    campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,

    job_type VARCHAR(50) NOT NULL CHECK (
        job_type IN (
            'transcribe',
            'find_clips',
            'render_clips',
            'full_pipeline'
        )
    ),

    status VARCHAR(50) NOT NULL DEFAULT 'queued' CHECK (
        status IN (
            'queued',
            'running',
            'succeeded',
            'failed',
            'cancelled'
        )
    ),

    progress_stage VARCHAR(100) NOT NULL DEFAULT 'queued',
    progress_percent INTEGER NOT NULL DEFAULT 0 CHECK (
        progress_percent >= 0 AND progress_percent <= 100
    ),

    error_message TEXT,

    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- =========================================================
-- Transcription
-- =========================================================

CREATE TABLE transcripts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    source_video_id UUID NOT NULL REFERENCES source_videos(id) ON DELETE CASCADE,

    provider VARCHAR(100) NOT NULL,
    model_name VARCHAR(255),

    language_code VARCHAR(20),

    full_text TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (source_video_id)
);


CREATE TABLE transcript_segments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    transcript_id UUID NOT NULL REFERENCES transcripts(id) ON DELETE CASCADE,

    segment_index INTEGER NOT NULL CHECK (segment_index >= 0),

    start_seconds NUMERIC(12, 3) NOT NULL CHECK (start_seconds >= 0),
    end_seconds NUMERIC(12, 3) NOT NULL CHECK (end_seconds >= 0),

    text TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (transcript_id, segment_index),
    CHECK (end_seconds >= start_seconds)
);


-- =========================================================
-- AI Clip Candidates
-- =========================================================

CREATE TABLE clip_candidates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    source_video_id UUID NOT NULL REFERENCES source_videos(id) ON DELETE CASCADE,
    campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,

    start_seconds NUMERIC(12, 3) NOT NULL CHECK (start_seconds >= 0),
    end_seconds NUMERIC(12, 3) NOT NULL CHECK (end_seconds >= 0),

    title VARCHAR(255),
    hook_text TEXT,
    suggested_caption TEXT,

    reason TEXT,

    score NUMERIC(5, 2) CHECK (score >= 0 AND score <= 100),

    ai_metadata JSONB,

    status VARCHAR(50) NOT NULL DEFAULT 'suggested' CHECK (
        status IN (
            'suggested',
            'accepted',
            'rejected',
            'rendered'
        )
    ),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CHECK (end_seconds > start_seconds)
);


-- =========================================================
-- Rendered Clips
-- =========================================================

CREATE TABLE rendered_clips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id VARCHAR(128) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    source_video_id UUID NOT NULL REFERENCES source_videos(id) ON DELETE CASCADE,
    campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,
    clip_candidate_id UUID REFERENCES clip_candidates(id) ON DELETE SET NULL,

    title VARCHAR(255),

    start_seconds NUMERIC(12, 3) NOT NULL CHECK (start_seconds >= 0),
    end_seconds NUMERIC(12, 3) NOT NULL CHECK (end_seconds >= 0),

    duration_seconds NUMERIC(12, 3) GENERATED ALWAYS AS (end_seconds - start_seconds) STORED,

    storage_bucket VARCHAR(255),
    storage_key TEXT NOT NULL,

    thumbnail_storage_key TEXT,

    width INTEGER NOT NULL,
    height INTEGER NOT NULL,

    has_burned_captions BOOLEAN NOT NULL DEFAULT false,

    caption_text TEXT,

    status VARCHAR(50) NOT NULL DEFAULT 'ready' CHECK (
        status IN (
            'rendering',
            'ready',
            'failed',
            'deleted'
        )
    ),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CHECK (end_seconds > start_seconds)
);


-- =========================================================
-- Compliance Checks
-- =========================================================

CREATE TABLE clip_compliance_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    rendered_clip_id UUID NOT NULL REFERENCES rendered_clips(id) ON DELETE CASCADE,
    campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,

    overall_status VARCHAR(50) NOT NULL CHECK (
        overall_status IN (
            'pass',
            'warning',
            'fail'
        )
    ),

    checklist_json JSONB NOT NULL,

    model_name VARCHAR(255),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- =========================================================
-- Manual / Imported Performance Tracking
-- =========================================================

CREATE TABLE clip_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id VARCHAR(128) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    rendered_clip_id UUID NOT NULL REFERENCES rendered_clips(id) ON DELETE CASCADE,

    platform VARCHAR(50) NOT NULL CHECK (
        platform IN (
            'tiktok',
            'instagram',
            'youtube',
            'facebook',
            'other'
        )
    ),

    post_url TEXT,

    posted_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


CREATE TABLE clip_performance_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    clip_post_id UUID NOT NULL REFERENCES clip_posts(id) ON DELETE CASCADE,

    views INTEGER NOT NULL DEFAULT 0 CHECK (views >= 0),
    likes INTEGER NOT NULL DEFAULT 0 CHECK (likes >= 0),
    comments INTEGER NOT NULL DEFAULT 0 CHECK (comments >= 0),
    shares INTEGER NOT NULL DEFAULT 0 CHECK (shares >= 0),
    saves INTEGER NOT NULL DEFAULT 0 CHECK (saves >= 0),

    engagement_rate NUMERIC(8, 4),

    source VARCHAR(50) NOT NULL DEFAULT 'manual' CHECK (
        source IN (
            'manual',
            'tiktok_api',
            'instagram_api',
            'youtube_api',
            'third_party_api'
        )
    ),

    captured_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- =========================================================
-- Affiliate / Referral System
-- =========================================================

CREATE TABLE affiliates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id VARCHAR(128) REFERENCES app_users(id) ON DELETE SET NULL,

    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,

    slug VARCHAR(100) NOT NULL UNIQUE,

    status VARCHAR(50) NOT NULL DEFAULT 'pending' CHECK (
        status IN (
            'pending',
            'approved',
            'rejected',
            'suspended',
            'closed'
        )
    ),

    commission_rate NUMERIC(5, 4) NOT NULL DEFAULT 0.3000 CHECK (
        commission_rate >= 0 AND commission_rate <= 1
    ),

    commission_duration_months INTEGER NOT NULL DEFAULT 12 CHECK (
        commission_duration_months > 0
    ),

    cookie_duration_days INTEGER NOT NULL DEFAULT 90 CHECK (
        cookie_duration_days > 0
    ),

    payout_delay_days INTEGER NOT NULL DEFAULT 45 CHECK (
        payout_delay_days >= 0
    ),

    minimum_payout_cents INTEGER NOT NULL DEFAULT 5000 CHECK (
        minimum_payout_cents >= 0
    ),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


CREATE TABLE referral_clicks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    affiliate_id UUID NOT NULL REFERENCES affiliates(id) ON DELETE CASCADE,

    visitor_id VARCHAR(255) NOT NULL,

    ip_hash VARCHAR(255),
    user_agent_hash VARCHAR(255),

    landing_url TEXT,
    referrer_url TEXT,

    clicked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,

    converted_user_id VARCHAR(128) REFERENCES app_users(id) ON DELETE SET NULL
);


CREATE TABLE affiliate_attributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    affiliate_id UUID NOT NULL REFERENCES affiliates(id) ON DELETE CASCADE,
    user_id VARCHAR(128) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    referral_click_id UUID REFERENCES referral_clicks(id) ON DELETE SET NULL,

    attributed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    commission_ends_at TIMESTAMPTZ NOT NULL,

    UNIQUE (user_id)
);


CREATE TABLE commissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    affiliate_id UUID NOT NULL REFERENCES affiliates(id) ON DELETE CASCADE,
    user_id VARCHAR(128) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    attribution_id UUID REFERENCES affiliate_attributions(id) ON DELETE SET NULL,

    stripe_invoice_id VARCHAR(255) UNIQUE,
    stripe_charge_id VARCHAR(255),

    amount_paid_cents INTEGER NOT NULL CHECK (amount_paid_cents >= 0),

    commission_rate NUMERIC(5, 4) NOT NULL CHECK (
        commission_rate >= 0 AND commission_rate <= 1
    ),

    commission_amount_cents INTEGER NOT NULL CHECK (commission_amount_cents >= 0),

    status VARCHAR(50) NOT NULL DEFAULT 'pending' CHECK (
        status IN (
            'pending',
            'approved',
            'cancelled',
            'paid'
        )
    ),

    available_after TIMESTAMPTZ NOT NULL,

    paid_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- =========================================================
-- Indexes
-- =========================================================

CREATE INDEX idx_app_users_stripe_customer_id
    ON app_users (stripe_customer_id);


CREATE INDEX idx_user_subscriptions_user_id
    ON user_subscriptions (user_id);


CREATE INDEX idx_user_subscriptions_stripe_subscription_id
    ON user_subscriptions (stripe_subscription_id);


CREATE INDEX idx_usage_periods_user_id
    ON usage_periods (user_id);


CREATE INDEX idx_campaigns_user_id
    ON campaigns (user_id);


CREATE INDEX idx_campaign_rule_sets_campaign_id
    ON campaign_rule_sets (campaign_id);


CREATE INDEX idx_source_videos_user_id
    ON source_videos (user_id);


CREATE INDEX idx_source_videos_campaign_id
    ON source_videos (campaign_id);


CREATE INDEX idx_video_jobs_user_id
    ON video_jobs (user_id);


CREATE INDEX idx_video_jobs_source_video_id
    ON video_jobs (source_video_id);


CREATE INDEX idx_video_jobs_status
    ON video_jobs (status);


CREATE INDEX idx_transcripts_source_video_id
    ON transcripts (source_video_id);


CREATE INDEX idx_transcript_segments_transcript_id
    ON transcript_segments (transcript_id);


CREATE INDEX idx_clip_candidates_source_video_id
    ON clip_candidates (source_video_id);


CREATE INDEX idx_clip_candidates_campaign_id
    ON clip_candidates (campaign_id);


CREATE INDEX idx_rendered_clips_user_id
    ON rendered_clips (user_id);


CREATE INDEX idx_rendered_clips_source_video_id
    ON rendered_clips (source_video_id);


CREATE INDEX idx_rendered_clips_campaign_id
    ON rendered_clips (campaign_id);


CREATE INDEX idx_clip_posts_user_id
    ON clip_posts (user_id);


CREATE INDEX idx_clip_posts_rendered_clip_id
    ON clip_posts (rendered_clip_id);


CREATE INDEX idx_clip_performance_snapshots_clip_post_id
    ON clip_performance_snapshots (clip_post_id);


CREATE INDEX idx_affiliates_slug
    ON affiliates (slug);


CREATE INDEX idx_referral_clicks_affiliate_id
    ON referral_clicks (affiliate_id);


CREATE INDEX idx_referral_clicks_visitor_id
    ON referral_clicks (visitor_id);


CREATE INDEX idx_affiliate_attributions_affiliate_id
    ON affiliate_attributions (affiliate_id);


CREATE INDEX idx_affiliate_attributions_user_id
    ON affiliate_attributions (user_id);


CREATE INDEX idx_commissions_affiliate_id
    ON commissions (affiliate_id);


CREATE INDEX idx_commissions_user_id
    ON commissions (user_id);


CREATE INDEX idx_commissions_status
    ON commissions (status);


CREATE INDEX idx_commissions_available_after
    ON commissions (available_after);


-- =========================================================
-- Seed Starter Plans
-- Stripe price IDs can be filled in later after you create products/prices in Stripe.
-- =========================================================

INSERT INTO subscription_plans (
    name,
    monthly_price_cents,
    source_minutes_per_month,
    rendered_clips_per_month,
    max_video_minutes,
    max_file_size_mb,
    allow_4k
)
VALUES
    ('Starter', 1900, 120, 25, 60, 2048, false),
    ('Pro', 4900, 600, 150, 180, 5120, false),
    ('Power', 9900, 1800, 400, 240, 10240, true);