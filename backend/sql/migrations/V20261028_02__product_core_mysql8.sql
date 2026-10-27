-- AgenticCPS MySQL 8 product-module baseline, version 20261028_02.
-- Derived from the 9 Product*DO entity classes and BaseDO in this repository,
-- NOT copied from H2 test fixtures or third-party commercial module SQL.
-- ONLY for an empty product namespace after explicit target preflight and backup.
-- Validate mapper contracts, JSON type handlers, tenant isolation and complete E2E before public launch.
-- Do not import sample users, balances, prices, orders or payment records.
CREATE TABLE `product_brand` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `pic_url` varchar(512) DEFAULT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `description` varchar(2048) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_product_brand_tenant_status` (`tenant_id`,`status`,`deleted`),
  KEY `idx_product_brand_tenant_name` (`tenant_id`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Product module: product_brand';

CREATE TABLE `product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint NOT NULL DEFAULT 0,
  `name` varchar(255) NOT NULL,
  `pic_url` varchar(512) DEFAULT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_product_category_parent` (`tenant_id`,`parent_id`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Product module: product_category';

CREATE TABLE `product_property` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_product_property_name` (`tenant_id`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Product module: product_property';

CREATE TABLE `product_property_value` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `property_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_property_value_property` (`tenant_id`,`property_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Product module: product_property_value';

CREATE TABLE `product_spu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `keyword` varchar(512) DEFAULT NULL,
  `introduction` varchar(1024) DEFAULT NULL,
  `description` longtext,
  `category_id` bigint NOT NULL,
  `brand_id` bigint DEFAULT NULL,
  `pic_url` varchar(512) DEFAULT NULL,
  `slider_pic_urls` json DEFAULT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0,
  `spec_type` bit(1) NOT NULL DEFAULT b'0',
  `price` int NOT NULL DEFAULT 0,
  `market_price` int NOT NULL DEFAULT 0,
  `cost_price` int NOT NULL DEFAULT 0,
  `stock` int NOT NULL DEFAULT 0,
  `delivery_types` varchar(255) DEFAULT NULL,
  `delivery_template_id` bigint DEFAULT NULL,
  `give_integral` int NOT NULL DEFAULT 0,
  `sub_commission_type` bit(1) NOT NULL DEFAULT b'0',
  `sales_count` int NOT NULL DEFAULT 0,
  `virtual_sales_count` int NOT NULL DEFAULT 0,
  `browse_count` int NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_product_spu_category` (`tenant_id`,`category_id`,`status`,`deleted`),
  KEY `idx_product_spu_brand` (`tenant_id`,`brand_id`,`deleted`),
  KEY `idx_product_spu_sort` (`tenant_id`,`sort`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Product module: product_spu';

CREATE TABLE `product_sku` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `spu_id` bigint NOT NULL,
  `properties` json DEFAULT NULL,
  `price` int NOT NULL DEFAULT 0,
  `market_price` int DEFAULT NULL,
  `cost_price` int NOT NULL DEFAULT 0,
  `bar_code` varchar(128) DEFAULT NULL,
  `pic_url` varchar(512) DEFAULT NULL,
  `stock` int NOT NULL DEFAULT 0,
  `weight` double DEFAULT NULL,
  `volume` double DEFAULT NULL,
  `first_brokerage_price` int DEFAULT NULL,
  `second_brokerage_price` int DEFAULT NULL,
  `sales_count` int NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_product_sku_spu` (`tenant_id`,`spu_id`,`deleted`),
  KEY `idx_product_sku_barcode` (`tenant_id`,`bar_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Product module: product_sku';

CREATE TABLE `product_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `spu_id` bigint NOT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_product_favorite_user` (`tenant_id`,`user_id`,`deleted`),
  KEY `idx_product_favorite_spu` (`tenant_id`,`spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Product module: product_favorite';

CREATE TABLE `product_browse_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `spu_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `user_deleted` bit(1) NOT NULL DEFAULT b'0',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_product_browse_user` (`tenant_id`,`user_id`,`user_deleted`,`create_time`),
  KEY `idx_product_browse_spu` (`tenant_id`,`spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Product module: product_browse_history';

CREATE TABLE `product_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `user_nickname` varchar(255) DEFAULT NULL,
  `user_avatar` varchar(512) DEFAULT NULL,
  `anonymous` bit(1) DEFAULT NULL,
  `order_id` bigint DEFAULT NULL,
  `order_item_id` bigint DEFAULT NULL,
  `spu_id` bigint NOT NULL,
  `spu_name` varchar(255) DEFAULT NULL,
  `sku_id` bigint DEFAULT NULL,
  `sku_pic_url` varchar(512) DEFAULT NULL,
  `sku_properties` json DEFAULT NULL,
  `visible` bit(1) NOT NULL DEFAULT b'1',
  `scores` tinyint DEFAULT NULL,
  `description_scores` tinyint DEFAULT NULL,
  `benefit_scores` tinyint DEFAULT NULL,
  `content` text,
  `pic_urls` json DEFAULT NULL,
  `reply_status` bit(1) NOT NULL DEFAULT b'0',
  `reply_user_id` bigint DEFAULT NULL,
  `reply_content` text,
  `reply_time` datetime DEFAULT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_product_comment_spu` (`tenant_id`,`spu_id`,`visible`,`deleted`),
  KEY `idx_product_comment_order` (`tenant_id`,`order_id`,`order_item_id`),
  KEY `idx_product_comment_user` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Product module: product_comment';
