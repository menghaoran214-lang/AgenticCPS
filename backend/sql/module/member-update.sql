-- Modification time (Asia/Shanghai): 2026-10-28 02:59:35; member v2.0.0 -> v2.0.1.
-- REVIEW ONLY: never apply to cps without a separate approved migration gate.
-- Prerequisite: V01 member baseline and V06 tenant alignment, exact old index verified.
-- Preflight: SELECT tenant_id,mobile,COUNT(*) FROM member_user
-- WHERE deleted=b'0' AND mobile IS NOT NULL GROUP BY tenant_id,mobile HAVING COUNT(*)>1;
-- Must return zero rows. MySQL ALTER is atomic but not transactionally rollbackable.
-- Retains every original mobile and deleted value. Multiple NULLs permit historical rows.
ALTER TABLE `member_user`
  ADD COLUMN `active_mobile` varchar(11) GENERATED ALWAYS AS
    (CASE WHEN `deleted` = b'0' THEN `mobile` ELSE NULL END) STORED,
  DROP INDEX `uk_member_tenant_mobile_deleted`,
  ADD UNIQUE KEY `uk_member_tenant_active_mobile` (`tenant_id`,`active_mobile`);

INSERT INTO `system_schema_version` (`module`,`version`,`schema_update_time`,`script_source`,`description`,`status`,`tenant_id`)
SELECT 'member','v2.0.1','2026-10-28 02:59:35','member-update.sql','Active member mobile uniqueness; retain repeated deletion history','SUCCESS',0
WHERE NOT EXISTS (SELECT 1 FROM `system_schema_version` WHERE `module`='member' AND `version`='v2.0.1');
