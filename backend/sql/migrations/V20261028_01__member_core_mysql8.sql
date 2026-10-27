-- AgenticCPS MySQL 8 member module baseline.
-- Source of truth: qiji-module-member Java DOs, BaseDO, TenantBaseDO and member_user Mapper.
-- The module test SQL is reference-only: it is NOT a production schema.
-- Apply to an empty member-module namespace only. Requires MySQL 8.0 and utf8mb4.
-- NEVER run with force; migration preflight must assert ALL six tables are absent.
-- Do not copy CI fixture passwords, test accounts or synthetic ledger records.

CREATE TABLE `member_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mobile` varchar(11) DEFAULT NULL,
  `password` varchar(100) NOT NULL DEFAULT '',
  `status` tinyint NOT NULL DEFAULT 0,
  `register_ip` varchar(45) NOT NULL DEFAULT '',
  `register_terminal` int DEFAULT NULL,
  `login_ip` varchar(45) DEFAULT NULL,
  `login_date` datetime DEFAULT NULL,
  `nickname` varchar(64) NOT NULL DEFAULT '',
  `avatar` varchar(512) NOT NULL DEFAULT '',
  `name` varchar(64) DEFAULT NULL,
  `sex` tinyint DEFAULT NULL,
  `birthday` datetime DEFAULT NULL,
  `area_id` int DEFAULT NULL,
  `mark` varchar(255) DEFAULT NULL,
  `point` int NOT NULL DEFAULT 0,
  `tag_ids` varchar(255) DEFAULT NULL,
  `level_id` bigint DEFAULT NULL,
  `experience` int NOT NULL DEFAULT 0,
  `group_id` bigint DEFAULT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_tenant_mobile_deleted` (`tenant_id`,`mobile`,`deleted`),
  KEY `idx_member_tenant_level` (`tenant_id`,`level_id`),
  KEY `idx_member_tenant_group` (`tenant_id`,`group_id`),
  KEY `idx_member_tenant_create` (`tenant_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Member accounts';

CREATE TABLE `member_address` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `name` varchar(64) NOT NULL,
  `mobile` varchar(20) NOT NULL,
  `area_id` bigint NOT NULL,
  `detail_address` varchar(250) NOT NULL,
  `default_status` bit(1) NOT NULL DEFAULT b'0',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_member_address_user` (`user_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Member delivery addresses';

CREATE TABLE `member_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_member_tag_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Member tags';

CREATE TABLE `member_level` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `level` int NOT NULL,
  `experience` int NOT NULL DEFAULT 0,
  `discount_percent` int NOT NULL DEFAULT 100,
  `icon` varchar(512) NOT NULL DEFAULT '',
  `background_url` varchar(512) NOT NULL DEFAULT '',
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_member_level` (`level`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Member levels';

CREATE TABLE `member_group` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `remark` varchar(255) NOT NULL DEFAULT '',
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_member_group_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Member groups';

CREATE TABLE `member_brokerage_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `biz_id` varchar(64) NOT NULL DEFAULT '',
  `biz_type` varchar(64) NOT NULL DEFAULT '',
  `title` varchar(255) NOT NULL DEFAULT '',
  `price` int NOT NULL DEFAULT 0,
  `total_price` int NOT NULL DEFAULT 0,
  `description` varchar(512) NOT NULL DEFAULT '',
  `status` varchar(32) NOT NULL DEFAULT '',
  `frozen_days` int NOT NULL DEFAULT 0,
  `unfreeze_time` varchar(64) DEFAULT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_member_brokerage_user` (`tenant_id`,`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Member brokerage records';
