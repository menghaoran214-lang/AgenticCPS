package com.qiji.cps.module.ai.dal.mysql.capability;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.mybatis.core.mapper.BaseMapperX;
import com.qiji.cps.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.qiji.cps.module.ai.controller.admin.capability.vo.AiMcpServerPageReqVO;
import com.qiji.cps.module.ai.dal.dataobject.capability.AiMcpServerDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface AiMcpServerMapper extends BaseMapperX<AiMcpServerDO> { default PageResult<AiMcpServerDO> selectPage(AiMcpServerPageReqVO r){ return selectPage(r,new LambdaQueryWrapperX<AiMcpServerDO>().likeIfPresent(AiMcpServerDO::getCode,r.getCode()).likeIfPresent(AiMcpServerDO::getName,r.getName()).eqIfPresent(AiMcpServerDO::getTransportProtocol,r.getTransportProtocol()).eqIfPresent(AiMcpServerDO::getStatus,r.getStatus()).eqIfPresent(AiMcpServerDO::getHealthStatus,r.getHealthStatus()).orderByDesc(AiMcpServerDO::getId)); } }
