CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id VARCHAR(128) REFERENCES app_users(id),
    actor_type VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(100),
    target_id UUID,
    request_id UUID,
    job_id UUID,
    ip_address VARCHAR(64),
    user_agent TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_actor_user_id
ON audit_logs(actor_user_id);

CREATE INDEX idx_audit_logs_action
ON audit_logs(action);

CREATE INDEX idx_audit_logs_target
ON audit_logs(target_type, target_id);

CREATE INDEX idx_audit_logs_created_at
ON audit_logs(created_at);

CREATE INDEX idx_audit_logs_job_id
ON audit_logs(job_id);
