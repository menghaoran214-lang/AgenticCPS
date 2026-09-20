package com.qiji.cps.module.ai.dal.mysql.model;

import com.qiji.cps.framework.mybatis.core.mapper.BaseMapperX;
import com.qiji.cps.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.qiji.cps.module.ai.dal.dataobject.model.AiChatRoleVersionDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface AiChatRoleVersionMapper extends BaseMapperX<AiChatRoleVersionDO> {
    default List<AiChatRoleVersionDO> selectHistory(Long roleId) {
        return selectList(new LambdaQueryWrapperX<AiChatRoleVersionDO>()
                .eq(AiChatRoleVersionDO::getRoleId, roleId)
                .orderByDesc(AiChatRoleVersionDO::getVersion).last("LIMIT 100"));
    }
}
