package com.qiji.cps.module.ai.controller.admin.capability;
import com.qiji.cps.framework.common.pojo.*;
import com.qiji.cps.framework.common.util.object.BeanUtils;
import com.qiji.cps.module.ai.controller.admin.capability.vo.*;
import com.qiji.cps.module.ai.dal.dataobject.capability.*;
import com.qiji.cps.module.ai.service.capability.AiMcpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.qiji.cps.framework.common.pojo.CommonResult.success;
@PreAuthorize("@ss.hasPermission('ai:mcp:query')")
@Tag(name="管理后台 - AI MCP") @RestController @RequestMapping("/ai/mcp") public class AiMcpController {
 @Resource private AiMcpService service;
 @Resource private com.qiji.cps.module.ai.service.capability.AiMcpAuditQueryService audit;
 @PreAuthorize("@ss.hasPermission('ai:mcp:manage')")
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PostMapping("/server/create") public CommonResult<Long> createServer(@Valid @RequestBody AiMcpServerSaveReqVO r){return success(service.createServer(r));}
 @PreAuthorize("@ss.hasPermission('ai:mcp:manage')")
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PutMapping("/server/update") public CommonResult<Boolean> updateServer(@Valid @RequestBody AiMcpServerSaveReqVO r){service.updateServer(r);return success(true);}
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @GetMapping("/server/get") public CommonResult<AiMcpServerRespVO> getServer(@RequestParam Long id){return success(BeanUtils.toBean(service.getServer(id),AiMcpServerRespVO.class));}
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @GetMapping("/server/page") public CommonResult<PageResult<AiMcpServerRespVO>> pageServers(@Valid AiMcpServerPageReqVO r){return success(BeanUtils.toBean(service.pageServers(r),AiMcpServerRespVO.class));}
 @PreAuthorize("@ss.hasPermission('ai:mcp:manage')")
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PostMapping("/server/test") public CommonResult<Boolean> testServer(@RequestParam Long id){return success(service.testServer(id));}
 @PreAuthorize("@ss.hasPermission('ai:mcp:manage')")
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PostMapping("/server/sync-tools") public CommonResult<Integer> syncTools(@RequestParam Long id){return success(service.syncTools(id));}
 @PreAuthorize("@ss.hasPermission('ai:mcp:manage')")
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PostMapping("/server/status") public CommonResult<Boolean> status(@RequestParam Long id,@RequestParam Integer status){service.setServerStatus(id,status);return success(true);}
 @PreAuthorize("@ss.hasPermission('ai:mcp:manage')")
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PostMapping("/tool/save") public CommonResult<Long> saveTool(@Valid @RequestBody AiMcpToolSaveReqVO r){return success(service.saveTool(r));}
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @GetMapping("/tool/page") public CommonResult<PageResult<AiMcpToolRespVO>> pageTools(@Valid AiMcpToolPageReqVO r){return success(BeanUtils.toBean(service.pageTools(r),AiMcpToolRespVO.class));}
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @GetMapping("/policy/get") public CommonResult<AiMcpPolicyRespVO> getPolicy(@RequestParam Long id){return success(BeanUtils.toBean(service.getPolicy(id),AiMcpPolicyRespVO.class));}
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @GetMapping("/policy/page") public CommonResult<PageResult<AiMcpPolicyRespVO>> pagePolicies(@Valid AiMcpPolicyPageReqVO r){return success(BeanUtils.toBean(service.pagePolicies(r),AiMcpPolicyRespVO.class));}
 @PreAuthorize("@ss.hasPermission('ai:mcp:manage')")
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @PostMapping("/policy/save") public CommonResult<Long> savePolicy(@Valid @RequestBody AiMcpPolicySaveReqVO r){return success(service.savePolicy(r));}
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @GetMapping("/log/page") @Operation(summary="MCP 调用审计") public CommonResult<PageResult<AiMcpLogRespVO>> logs(@Valid AiMcpLogPageReqVO r){return success(audit.page(r));}
 @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
 @GetMapping("/log/stats") public CommonResult<Map<String,Object>> stats(@Valid AiMcpLogPageReqVO r){return success(audit.stats(r));}
}
