package com.qiji.cps.module.cps.service.version;

import com.qiji.cps.module.cps.dal.dataobject.version.CpsSchemaVersionDO;

import java.util.List;

/**
 * 数据库结构版本与更新时间 Service 接口
 *
 * @author CPS System
 */
public interface CpsSchemaVersionService {

    /**
     * 获取指定模块的最新数据库结构版本信息
     *
     * @param module 模块标识（如 cps）
     * @return 最新结构版本记录
     */
    CpsSchemaVersionDO getLatestSchemaVersion(String module);

    /**
     * 获取指定模块的结构版本历史列表
     *
     * @param module 模块标识（为 null 时查询所有模块）
     * @return 结构版本历史列表
     */
    List<CpsSchemaVersionDO> listSchemaVersions(String module);

}
