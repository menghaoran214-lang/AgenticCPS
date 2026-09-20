package com.qiji.cps.module.ai.controller.admin.capability;
import com.qiji.cps.framework.common.pojo.CommonResult;
import com.qiji.cps.module.ai.service.capability.AiCapabilityRuntime;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.qiji.cps.framework.common.pojo.CommonResult.success;
import static com.qiji.cps.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@RestController
@RequestMapping("/ai/chat/conversation")
public class AiToolConfirmationController {
    @Resource private AiCapabilityRuntime runtime;
    @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
    @GetMapping("/tool-confirmations")
    public CommonResult<List<Map<String,Object>>> pending(@RequestParam Long conversationId) {
        return success(runtime.pending(conversationId,getLoginUserId()));
    }
    @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)
    @PostMapping("/confirm-tool")
    public CommonResult<String> confirm(@RequestParam Long id) { return success(runtime.confirm(id,getLoginUserId())); }
}
