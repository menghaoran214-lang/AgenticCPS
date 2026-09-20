package com.qiji.cps.module.ai.dal.mysql.capability;
import com.qiji.cps.framework.mybatis.core.mapper.BaseMapperX;
import com.qiji.cps.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.module.ai.controller.admin.capability.vo.AiMcpPolicyPageReqVO;
import com.qiji.cps.module.ai.dal.dataobject.capability.AiMcpPolicyDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper public interface AiMcpPolicyMapper extends BaseMapperX<AiMcpPolicyDO> {
    default List<AiMcpPolicyDO> selectListByRoleId(Long id){
        return selectList(new LambdaQueryWrapperX<AiMcpPolicyDO>()
                .eq(AiMcpPolicyDO::getRoleId,id).orderByDesc(AiMcpPolicyDO::getId));
    }
    default PageResult<AiMcpPolicyDO> selectPage(AiMcpPolicyPageReqVO req) {
        return selectPage(req, new LambdaQueryWrapperX<AiMcpPolicyDO>()
                .likeIfPresent(AiMcpPolicyDO::getCode, req.getCode())
                .likeIfPresent(AiMcpPolicyDO::getName, req.getName())
                .eqIfPresent(AiMcpPolicyDO::getStatus, req.getStatus())
                .orderByDesc(AiMcpPolicyDO::getId));
    }
}
