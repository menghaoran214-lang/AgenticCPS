package com.qiji.cps.module.ai.dal.mysql.capability;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.mybatis.core.mapper.BaseMapperX;
import com.qiji.cps.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.qiji.cps.module.ai.controller.admin.capability.vo.AiSkillPageReqVO;
import com.qiji.cps.module.ai.dal.dataobject.capability.AiSkillDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface AiSkillMapper extends BaseMapperX<AiSkillDO> { default AiSkillDO selectForUpdate(Long id) { return selectOne(new LambdaQueryWrapperX<AiSkillDO>().eq(AiSkillDO::getId, id).last("FOR UPDATE")); } default PageResult<AiSkillDO> selectPage(AiSkillPageReqVO r){ return selectPage(r,new LambdaQueryWrapperX<AiSkillDO>().likeIfPresent(AiSkillDO::getCode,r.getCode()).likeIfPresent(AiSkillDO::getName,r.getName()).eqIfPresent(AiSkillDO::getCategory,r.getCategory()).eqIfPresent(AiSkillDO::getRiskLevel,r.getRiskLevel()).eqIfPresent(AiSkillDO::getStatus,r.getStatus()).orderByDesc(AiSkillDO::getId)); } }
