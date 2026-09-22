package com.qiji.cps.module.ai.service.capability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.common.util.json.JsonUtils;
import com.qiji.cps.framework.common.util.object.BeanUtils;
import com.qiji.cps.module.ai.controller.admin.capability.vo.*;
import com.qiji.cps.module.ai.dal.dataobject.capability.*;
import com.qiji.cps.module.ai.dal.mysql.capability.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

import static com.qiji.cps.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.qiji.cps.module.ai.enums.ErrorCodeConstants.MCP_SERVER_TEST_FAILED;

@Service
public class AiMcpServiceImpl implements AiMcpService {
    @Resource private AiMcpServerMapper serverMapper;
    @Resource private AiMcpToolMapper toolMapper;
    @Resource private AiMcpPolicyMapper policyMapper;
    @Resource private McpRegistryService registry;
    public Long createServer(AiMcpServerSaveReqVO req) {
        AiMcpServerDO server=BeanUtils.toBean(req,AiMcpServerDO.class).setId(null);
        normalizeSecrets(server);
        defaults(server); validate(server);
        require(serverMapper.selectCount(new LambdaQueryWrapper<AiMcpServerDO>().eq(AiMcpServerDO::getCode,server.getCode()))==0,"服务编码已存在");
        serverMapper.insert(server); return server.getId();
    }
    public void updateServer(AiMcpServerSaveReqVO req) {
        var current=getRequiredServer(req.getId());
        require(Objects.equals(current.getCode(),req.getCode()),"服务编码创建后不可修改");
        var server=BeanUtils.toBean(req,AiMcpServerDO.class);
        preserveOrReplaceSecrets(current, server);
        defaults(server); validate(server);
        serverMapper.updateById(server); registry.invalidate(server.getId());
    }
    public AiMcpServerDO getServer(Long id) { return getRequiredServer(id); }
    public PageResult<AiMcpServerDO> pageServers(AiMcpServerPageReqVO req) { return serverMapper.selectPage(req); }
    public void setServerStatus(Long id,Integer status) {
        getRequiredServer(id); require(status != null && (status==0 || status==1),"状态无效");
        serverMapper.updateById(new AiMcpServerDO().setId(id).setStatus(status)); registry.invalidate(id);
    }
    public boolean testServer(Long id) {
        getRequiredServer(id);
        try {
            registry.inspect(id);
            return true;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw exception(MCP_SERVER_TEST_FAILED, McpConnectionManager.describeFailure(ex));
        }
    }
    @Transactional(rollbackFor=Exception.class)
    public int syncTools(Long id) {
        getRequiredServer(id);
        var discovery=registry.inspect(id);
        var existing=toolMapper.selectList(new LambdaQueryWrapper<AiMcpToolDO>().eq(AiMcpToolDO::getServerId,id));
        Map<String,AiMcpToolDO> names=new HashMap<>(); existing.forEach(tool->names.put(tool.getName(),tool));
        Set<String> discovered=new HashSet<>();
        for (var tool:discovery.tools()) {
            require(discovered.add(tool.name()),"服务返回重复工具名称");
            AiMcpToolDO saved=names.get(tool.name());
            boolean isNew=saved==null;
            if(isNew) saved=new AiMcpToolDO().setServerId(id).setName(tool.name()).setStatus(1).setRiskLevel("ADMIN");
            saved.setDescription(tool.description()).setInputSchema(JsonUtils.toJsonString(tool.inputSchema()))
                    .setOutputSchema(tool.outputSchema()==null ? null : JsonUtils.toJsonString(tool.outputSchema()))
                    .setSourceVersion(discovery.version()).setSyncStatus("SYNCED");
            if (isNew || saved.getTitle() == null) saved.setTitle(tool.name());
            // Newly discovered tools stay disabled until an operator reviews their risk.
            if(isNew) toolMapper.insert(saved); else toolMapper.updateById(saved);
        }
        for(var old:existing) if(!discovered.contains(old.getName()))
            toolMapper.updateById(new AiMcpToolDO().setId(old.getId()).setSyncStatus("MISSING").setStatus(1));
        return discovered.size();
    }
    public Long saveTool(AiMcpToolSaveReqVO req) {
        require(req.getId()!=null,"工具必须先从服务发现");
        var current=toolMapper.selectById(req.getId()); require(current!=null,"工具不存在");
        require(AiCapabilitySnapshotService.rank(req.getRiskLevel())>=0,"风险等级无效");
        require(req.getStatus()!=null && (req.getStatus()==0 || req.getStatus()==1),"状态无效");
        require(!"MISSING".equals(current.getSyncStatus()) || req.getStatus()==1,"工具已从服务移除");
        toolMapper.updateById(new AiMcpToolDO().setId(current.getId()).setRiskLevel(req.getRiskLevel())
                .setStatus(req.getStatus()).setTitle(req.getTitle())); return current.getId();
    }
    public PageResult<AiMcpToolDO> pageTools(AiMcpToolPageReqVO req) {return toolMapper.selectPage(req);}
    public Long savePolicy(AiMcpPolicySaveReqVO req) {
        require(req.getCode()!=null && req.getCode().matches("[a-zA-Z0-9_-]{1,64}"),"策略编码无效");
        require(req.getName()!=null && !req.getName().isBlank(),"策略名称不能为空");
        require(req.getName().length() <= 128,"策略名称过长");
        require(req.getStatus()==null || req.getStatus()==0 || req.getStatus()==1,"状态无效");
        require(req.getRequireConfirmation()!=null && (req.getRequireConfirmation()==0 || req.getRequireConfirmation()==1),"确认策略无效");
        require(AiCapabilitySnapshotService.rank(req.getRiskLevel())>=0,"风险等级无效");
        require("READ_ONLY".equals(req.getRiskLevel()) || Integer.valueOf(1).equals(req.getRequireConfirmation()),"写操作必须确认");
        var servers=AiCapabilitySnapshotService.ids(req.getMcpServerIds()); servers.forEach(this::getRequiredServer);
        for(Long id:AiCapabilitySnapshotService.ids(req.getMcpToolIds())) {
            var tool=toolMapper.selectById(id); require(tool!=null && servers.contains(tool.getServerId()),"工具不属于已授权服务");
        }
        if(req.getId()!=null) require(policyMapper.selectById(req.getId())!=null,"策略不存在");
        require(policyMapper.selectCount(new LambdaQueryWrapper<AiMcpPolicyDO>().eq(AiMcpPolicyDO::getCode,req.getCode())
                .ne(req.getId()!=null,AiMcpPolicyDO::getId,req.getId()))==0,"策略编码已存在");
        var policy=BeanUtils.toBean(req,AiMcpPolicyDO.class); if(policy.getStatus()==null) policy.setStatus(0);
        policyMapper.insertOrUpdate(policy); return policy.getId();
    }
    public AiMcpPolicyDO getPolicy(Long id) { return policyMapper.selectById(id); }
    public PageResult<AiMcpPolicyDO> pagePolicies(AiMcpPolicyPageReqVO req) { return policyMapper.selectPage(req); }
    private AiMcpServerDO getRequiredServer(Long id) {
        require(id!=null,"服务编号不能为空"); var server=serverMapper.selectById(id);require(server!=null,"服务不存在");return server;
    }
    private void defaults(AiMcpServerDO server) {
        if(server.getStatus()==null) server.setStatus(1);
        if(server.getTimeoutMillis()==null) server.setTimeoutMillis(10000);
        if(server.getAuthType()==null) server.setAuthType("NONE");
        if(server.getLocation()==null) server.setLocation("REMOTE");
        server.setHealthStatus("UNKNOWN");
    }

    private void normalizeSecrets(AiMcpServerDO server) {
        if ("NONE".equals(server.getAuthType())) {
            server.setSecretRef(null).setSecretValue(null);
        } else if ("BEARER".equals(server.getAuthType())) {
            server.setSecretValue(null);
        } else if ("API_KEY".equals(server.getAuthType()) && server.getSecretValue() != null) {
            server.setSecretValue(server.getSecretValue().trim());
            if (!server.getSecretValue().isBlank()) server.setSecretRef(null);
        }
    }

    private void preserveOrReplaceSecrets(AiMcpServerDO current, AiMcpServerDO server) {
        if ("NONE".equals(server.getAuthType())) {
            server.setSecretRef(null).setSecretValue(null);
            return;
        }
        if ("BEARER".equals(server.getAuthType())) {
            server.setSecretValue(null);
            return;
        }
        if ("API_KEY".equals(server.getAuthType())) {
            if (server.getSecretValue() == null || server.getSecretValue().isBlank()) {
                server.setSecretValue(current.getSecretValue());
                if (server.getSecretRef() == null || server.getSecretRef().isBlank())
                    server.setSecretRef(current.getSecretRef());
            } else {
                server.setSecretValue(server.getSecretValue().trim()).setSecretRef(null);
            }
        }
    }
    private void validate(AiMcpServerDO server) {
        require(server.getCode()!=null && server.getCode().matches("[a-zA-Z0-9_-]{1,64}"),"服务编码无效");
        require(server.getName()!=null && !server.getName().isBlank(),"服务名称不能为空");
        require(server.getName().length() <= 128,"服务名称过长");
        require(server.getStatus()!=null && (server.getStatus()==0 || server.getStatus()==1),"状态无效");
        require(Set.of("LOCAL","REMOTE").contains(server.getLocation()),"服务位置无效");
        require(server.getEndpoint()!=null && server.getEndpoint().length() <= 1024,"服务地址无效");
        McpConnectionManager.validateConnection(server);
    }
    private static void require(boolean value,String message) { if(!value) throw new IllegalArgumentException(message); }
}
