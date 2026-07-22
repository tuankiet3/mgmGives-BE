-- The FINAL_REPORT media context didn't exist before this release: campaigns whose final
-- report was already published had their report media living under the default context
-- ('CAMPAIGN'). Retag it now so it isn't lost from the report view once the application
-- starts filtering by context.
UPDATE campaign_medias m
SET context = 'FINAL_REPORT'
FROM campaigns c
WHERE m.campaign_id = c.id
  AND c.result_posted = true
  AND m.context = 'CAMPAIGN'
  AND m.is_cover = false
  AND m.deleted_at IS NULL;

-- Announcement/meeting attachment media is scoped by its own FK (announcement_id / meeting_id),
-- not by context - it belongs in the general campaign gallery like everything else. Reset any
-- rows tagged that way during earlier iteration of this feature back to the default.
UPDATE campaign_medias
SET context = 'CAMPAIGN'
WHERE context IN ('ANNOUNCEMENT', 'MEETING');

-- Enforce valid context values at the DB level going forward - context is otherwise a
-- free-text column only checked by scattered application-level whitelists.
ALTER TABLE campaign_medias
    ADD CONSTRAINT chk_campaign_medias_context
    CHECK (context IN ('CAMPAIGN', 'FINAL_REPORT'));
