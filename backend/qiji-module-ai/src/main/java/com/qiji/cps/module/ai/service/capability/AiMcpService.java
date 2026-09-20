package com.qiji.cps.module.ai.service.capability;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.module.ai.controller.admin.capability.vo.*;
import com.qiji.cps.module.ai.dal.dataobject.capability.*;
public interface AiMcpService { Long createServer(AiMcpServerSaveReqVO req); void updateServer(AiMcpServerSaveReqVO req); AiMcpServerDO getServer(Long id); PageResult<AiMcpServerDO> pageServers(AiMcpServerPageReqVO req); void setServerStatus(Long id,Integer status); boolean testServer(Long id); int syncTools(Long id); Long saveTool(AiMcpToolSaveReqVO req); PageResult<AiMcpToolDO> pageTools(AiMcpToolPageReqVO req); Long savePolicy(AiMcpPolicySaveReqVO req); AiMcpPolicyDO getPolicy(Long id); PageResult<AiMcpPolicyDO> pagePolicies(AiMcpPolicyPageReqVO req); }
