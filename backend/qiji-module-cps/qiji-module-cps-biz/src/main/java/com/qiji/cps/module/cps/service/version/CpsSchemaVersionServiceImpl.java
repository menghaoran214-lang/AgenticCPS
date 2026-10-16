package com.qiji.cps.module.cps.service.version;

import com.qiji.cps.module.cps.dal.dataobject.version.CpsSchemaVersionDO;
import com.qiji.cps.module.cps.dal.mysql.version.CpsSchemaVersionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 数据库结构版本与更新时间 Service 实现类
 *
 * @author CPS System
 */
@Service
@Validated
@Slf4j
public class CpsSchemaVersionServiceImpl implements CpsSchemaVersionService {

    @Resource
    private CpsSchemaVersionMapper schemaVersionMapper;

    @Override
    public CpsSchemaVersionDO getLatestSchemaVersion(String module) {
        String effectiveModule = (module != null && !module.isBlank()) ? module : "cps";
        return schemaVersionMapper.selectLatestByModule(effectiveModule);
    }

    @Override
    public List<CpsSchemaVersionDO> listSchemaVersions(String module) {
        return schemaVersionMapper.selectListByModule(module);
    }

}
