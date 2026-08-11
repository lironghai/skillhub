-- V47__skill_bundle_download_count.sql

ALTER TABLE skill_bundle
    ADD COLUMN download_count BIGINT NOT NULL DEFAULT 0;
