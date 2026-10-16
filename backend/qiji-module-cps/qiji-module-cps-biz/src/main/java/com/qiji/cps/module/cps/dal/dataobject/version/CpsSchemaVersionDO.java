package com.qiji.cps.module.cps.dal.dataobject.version;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qiji.cps.framework.tenant.core.db.TenantBaseDO;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 数据库结构版本与更新时间追踪 DO
 *
 * @author CPS System
 */
@TableName("system_schema_version")
@KeySequence("system_schema_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpsSchemaVersionDO extends TenantBaseDO {

    /**
     * 主键ID
     */
    @TableId
    private Long id;

    /**
     * 模块名称（cps/ai/mall等）
     */
    private String module;

    /**
     * 结构版本号，如 v2.1.0
     */
    private String version;

    /**
     * 数据库结构版本对应的更新时间
     */
    private LocalDateTime schemaUpdateTime;

    /**
     * 本环境执行应用时间
     */
    private LocalDateTime appliedTime;

    /**
     * 来源脚本文件（cps-all-in-one.sql / cps-update.sql）
     */
    private String scriptSource;

    /**
     * 版本描述与主要改动
     */
    private String description;

    /**
     * 状态（SUCCESS/EXECUTING/FAILED）
     */
    private String status;

}
