-- AgenticCPS V20261028_06: reconcile four member BaseDO tables with TenantLineInnerInterceptor.
-- Source: TenantDatabaseInterceptor ignores only explicitly excluded/annotated tables.
-- MemberAddress/Tag/Level/Group are not excluded and have no @TenantIgnore.
-- At runtime the tenant interceptor requires tenant_id on all four tables.
-- IMPORTANT: existing rows must be zero before applying, or historical tenant ownership
-- must be reconstructed manually. Do NOT assign tenant 1 to unknown existing records.
-- Additive only: no DELETE/UPDATE/DROP and no permission to deploy by itself.

ALTER TABLE `member_address`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1,
  ADD KEY `idx_member_address_tenant_user` (`tenant_id`,`user_id`,`deleted`);

ALTER TABLE `member_tag`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1,
  ADD KEY `idx_member_tag_tenant_name` (`tenant_id`,`name`);

ALTER TABLE `member_level`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1,
  ADD KEY `idx_member_level_tenant_level` (`tenant_id`,`level`,`deleted`);

ALTER TABLE `member_group`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1,
  ADD KEY `idx_member_group_tenant_name` (`tenant_id`,`name`);
