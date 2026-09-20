package com.qiji.cps.module.ai.dal.mysql.capability;
import com.qiji.cps.framework.mybatis.core.mapper.BaseMapperX;
import com.qiji.cps.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.qiji.cps.module.ai.dal.dataobject.capability.AiSkillBindingDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper public interface AiSkillBindingMapper extends BaseMapperX<AiSkillBindingDO> { default List<AiSkillBindingDO> selectListByVersionId(Long id){ return selectList(new LambdaQueryWrapperX<AiSkillBindingDO>().eq(AiSkillBindingDO::getSkillVersionId,id).orderByAsc(AiSkillBindingDO::getId)); } }
