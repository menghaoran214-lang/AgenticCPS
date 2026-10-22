package com.qiji.cps.module.cps.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.qiji.cps.framework.common.util.json.JsonUtils;
import com.qiji.cps.framework.quartz.core.handler.JobHandler;
import com.qiji.cps.framework.tenant.core.job.TenantJob;
import com.qiji.cps.module.cps.service.selection.CpsSelectionThemeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * CPS 选品商品价格趋势跟踪定时拉取 Job.
 *
 * <p>定期拉取已开启价格跟踪的选品商品价格快照，形成价格历史走势。</p>
 */
@Slf4j
@Component("cpsGoodsPriceTrackingJob")
public class CpsGoodsPriceTrackingJob implements JobHandler {

    @Resource
    private CpsSelectionThemeService selectionThemeService;

    @Override
    @TenantJob
    public String execute(String param) {
        Integer batchSize = parseBatchSize(param);
        log.info("[CpsGoodsPriceTrackingJob] 开始执行选品商品价格趋势跟踪，batchSize={}", batchSize);
        String result = selectionThemeService.trackTrackedItemPrices(batchSize);
        log.info("[CpsGoodsPriceTrackingJob] {}", result);
        return result;
    }

    private Integer parseBatchSize(String param) {
        if (!StringUtils.hasText(param)) {
            return 50;
        }
        try {
            JsonNode node = JsonUtils.parseTree(param);
            if (node != null && node.has("batchSize")) {
                return node.get("batchSize").asInt(50);
            }
        } catch (Exception e) {
            log.warn("[CpsGoodsPriceTrackingJob] 参数解析异常，使用默认batchSize=50: param={}", param);
        }
        return 50;
    }
}
