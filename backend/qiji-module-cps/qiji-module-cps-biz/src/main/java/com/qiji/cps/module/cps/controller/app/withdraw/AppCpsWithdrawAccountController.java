package com.qiji.cps.module.cps.controller.app.withdraw;

import com.qiji.cps.framework.common.pojo.CommonResult;
import com.qiji.cps.framework.security.core.util.SecurityFrameworkUtils;
import com.qiji.cps.module.cps.controller.app.withdraw.vo.AppCpsWithdrawAccountRespVO;
import com.qiji.cps.module.cps.controller.app.withdraw.vo.AppCpsWithdrawAccountSaveReqVO;
import com.qiji.cps.module.cps.service.withdraw.CpsWithdrawAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.qiji.cps.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 APP - CPS 提现账户")
@RestController
@RequestMapping("/cps/withdraw-account")
@Validated
public class AppCpsWithdrawAccountController {

    @Resource
    private CpsWithdrawAccountService withdrawAccountService;

    @GetMapping("/list")
    @Operation(summary = "获取我的提现账户列表")
    public CommonResult<List<AppCpsWithdrawAccountRespVO>> getMyAccountList() {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        return success(withdrawAccountService.getMemberAccountList(memberId));
    }

    @PostMapping("/save")
    @Operation(summary = "保存提现账户（新增或修改）")
    public CommonResult<Long> saveAccount(@Valid @RequestBody AppCpsWithdrawAccountSaveReqVO request) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        return success(withdrawAccountService.saveAccount(memberId, request));
    }

    @PutMapping("/set-default")
    @Operation(summary = "设置默认提现账户")
    public CommonResult<Boolean> setDefaultAccount(@RequestParam("id") Long id) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        withdrawAccountService.setDefaultAccount(memberId, id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除提现账户")
    public CommonResult<Boolean> deleteAccount(@RequestParam("id") Long id) {
        Long memberId = SecurityFrameworkUtils.getLoginUserId();
        withdrawAccountService.deleteAccount(memberId, id);
        return success(true);
    }

}
