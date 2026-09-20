package com.qiji.cps.module.ai.dal.mysql.capability;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.mybatis.core.mapper.BaseMapperX;
import com.qiji.cps.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.qiji.cps.module.ai.controller.admin.capability.vo.AiMcpToolPageReqVO;
import com.qiji.cps.module.ai.dal.dataobject.capability.AiMcpToolDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface AiMcpToolMapper extends BaseMapperX<AiMcpToolDO> { default PageResult<AiMcpToolDO> selectPage(AiMcpToolPageReqVO r){ return selectPage(r,new LambdaQueryWrapperX<AiMcpToolDO>().eqIfPresent(AiMcpToolDO::getServerId,r.getServerId()).likeIfPresent(AiMcpToolDO::getName,r.getName()).eqIfPresent(AiMcpToolDO::getRiskLevel,r.getRiskLevel()).eqIfPresent(AiMcpToolDO::getStatus,r.getStatus()).orderByDesc(AiMcpToolDO::getId)); } }
