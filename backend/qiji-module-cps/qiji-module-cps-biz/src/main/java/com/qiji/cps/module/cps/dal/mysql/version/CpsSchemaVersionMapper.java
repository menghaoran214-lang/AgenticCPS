package com.qiji.cps.module.cps.dal.mysql.version;

import com.qiji.cps.framework.mybatis.core.mapper.BaseMapperX;
import com.qiji.cps.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.qiji.cps.module.cps.dal.dataobject.version.CpsSchemaVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 数据库结构版本与更新时间追踪 Mapper
 *
 * @author CPS System
 */
@Mapper
public interface CpsSchemaVersionMapper extends BaseMapperX<CpsSchemaVersionDO> {

    default CpsSchemaVersionDO selectLatestByModule(String module) {
        return selectOne(new LambdaQueryWrapperX<CpsSchemaVersionDO>()
                .eq(CpsSchemaVersionDO::getModule, module)
                .orderByDesc(CpsSchemaVersionDO::getSchemaUpdateTime)
                .last("LIMIT 1"));
    }

    default List<CpsSchemaVersionDO> selectListByModule(String module) {
        return selectList(new LambdaQueryWrapperX<CpsSchemaVersionDO>()
                .eqIfPresent(CpsSchemaVersionDO::getModule, module)
                .orderByDesc(CpsSchemaVersionDO::getSchemaUpdateTime));
    }

}
