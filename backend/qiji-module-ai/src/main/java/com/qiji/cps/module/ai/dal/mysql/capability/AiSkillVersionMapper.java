package com.qiji.cps.module.ai.dal.mysql.capability;
import com.qiji.cps.framework.mybatis.core.mapper.BaseMapperX;
import com.qiji.cps.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.qiji.cps.module.ai.dal.dataobject.capability.AiSkillVersionDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper public interface AiSkillVersionMapper extends BaseMapperX<AiSkillVersionDO> { default AiSkillVersionDO selectForUpdate(Long id) { return selectOne(new LambdaQueryWrapperX<AiSkillVersionDO>().eq(AiSkillVersionDO::getId, id).last("FOR UPDATE")); } default List<AiSkillVersionDO> selectListBySkillId(Long id){ return selectList(new LambdaQueryWrapperX<AiSkillVersionDO>().eq(AiSkillVersionDO::getSkillId,id).orderByDesc(AiSkillVersionDO::getVersion)); } default AiSkillVersionDO selectBySkillIdAndVersion(Long id,Integer v){ return selectOne(new LambdaQueryWrapperX<AiSkillVersionDO>().eq(AiSkillVersionDO::getSkillId,id).eq(AiSkillVersionDO::getVersion,v)); } }
