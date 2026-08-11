-- V45__skill_bundle_tables.sql

CREATE TABLE skill_bundle (
    id BIGSERIAL PRIMARY KEY,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    slug VARCHAR(128) NOT NULL,
    name VARCHAR(200) NOT NULL,
    summary VARCHAR(512),
    description TEXT,
    role_description TEXT,
    applicable_scenarios TEXT,
    methodology TEXT,
    recommended_skill_notes TEXT,
    visibility VARCHAR(20) NOT NULL CHECK (visibility IN ('PUBLIC', 'NAMESPACE_ONLY', 'PRIVATE')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    owner_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    created_by VARCHAR(128) REFERENCES user_account(id),
    updated_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(namespace_id, slug)
);

CREATE INDEX idx_skill_bundle_namespace_status ON skill_bundle(namespace_id, status, updated_at DESC, id DESC);
CREATE INDEX idx_skill_bundle_visibility_status ON skill_bundle(visibility, status, updated_at DESC, id DESC);
CREATE INDEX idx_skill_bundle_owner ON skill_bundle(owner_id, updated_at DESC, id DESC);

CREATE TABLE skill_bundle_item (
    id BIGSERIAL PRIMARY KEY,
    bundle_id BIGINT NOT NULL REFERENCES skill_bundle(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(bundle_id, skill_id)
);

CREATE INDEX idx_skill_bundle_item_bundle_order ON skill_bundle_item(bundle_id, sort_order, id);
CREATE INDEX idx_skill_bundle_item_skill ON skill_bundle_item(skill_id);

CREATE TABLE skill_bundle_label (
    id BIGSERIAL PRIMARY KEY,
    bundle_id BIGINT NOT NULL REFERENCES skill_bundle(id) ON DELETE CASCADE,
    label_id BIGINT NOT NULL REFERENCES label_definition(id) ON DELETE CASCADE,
    created_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(bundle_id, label_id)
);

CREATE INDEX idx_skill_bundle_label_bundle ON skill_bundle_label(bundle_id);
CREATE INDEX idx_skill_bundle_label_label ON skill_bundle_label(label_id);
