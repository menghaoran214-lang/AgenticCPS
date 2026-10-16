package com.qiji.cps.module.cps.client.taobao;

import com.qiji.cps.module.cps.client.CpsApiVendorClient;
import com.qiji.cps.module.cps.client.CpsPlatformClient;
import com.qiji.cps.module.cps.client.CpsPlatformClientFactory;
import com.qiji.cps.module.cps.client.CpsVendorException;
import com.qiji.cps.module.cps.client.dto.*;
import com.qiji.cps.module.cps.enums.CpsPlatformCodeEnum;
import com.qiji.cps.module.cps.enums.CpsVendorCodeEnum;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 淘宝联盟平台适配器（委托模式）
 *
 * <p>面向业务层的顶层适配器，通过 {@link CpsPlatformClientFactory} 获取当前激活的
 * {@link CpsApiVendorClient} 实现，委托执行具体的 API 调用。</p>
 *
 * <p>支持的供应商：大淘客、好单库、淘宝联盟官方API（支持主备供应商智能兜底与召回互补）</p>
 *
 * @author CPS System
 */
@Slf4j
@Component
public class TaobaoPlatformClientAdapter implements CpsPlatformClient {

    @Resource
    private CpsPlatformClientFactory factory;

    @Override
    public String getPlatformCode() {
        return CpsPlatformCodeEnum.TAOBAO.getCode();
    }

    @Override
    public CpsGoodsSearchResult searchGoods(CpsGoodsSearchRequest request) {
        CpsApiVendorClient vendor = factory.getActiveVendorClient(getPlatformCode());
        CpsVendorConfig config = factory.getActiveVendorConfig(getPlatformCode());
        if (vendor == null || config == null) {
            log.warn("[淘宝适配器] 未找到激活的供应商或配置");
            return buildEmptyResult(request);
        }

        CpsGoodsSearchResult result = null;
        try {
            result = vendor.searchGoods(request, config);
        } catch (Exception e) {
            log.warn("[淘宝适配器] 主供应商 {} 搜索异常: {}", vendor.getVendorCode(), e.getMessage());
        }

        if (result != null && result.getList() != null && !result.getList().isEmpty()) {
            return result;
        }

        // 当主供应商（如大淘客选品库）搜索无结果或异常时，尝试从备选供应商（如好单库全网超级搜索）进行召回补量
        String fallbackVendorCode = resolveFallbackVendorCode(vendor.getVendorCode());
        CpsApiVendorClient fallbackVendor = factory.getVendorClient(fallbackVendorCode, getPlatformCode());
        CpsVendorConfig fallbackConfig = factory.getVendorConfig(fallbackVendorCode, getPlatformCode());
        if (fallbackVendor != null && fallbackConfig != null) {
            log.info("[淘宝适配器] 主供应商 {} 搜索结果为空，触发兜底供应商 {} 搜索补量",
                    vendor.getVendorCode(), fallbackVendorCode);
            try {
                CpsGoodsSearchResult fallbackResult = fallbackVendor.searchGoods(request, fallbackConfig);
                if (fallbackResult != null && fallbackResult.getList() != null && !fallbackResult.getList().isEmpty()) {
                    return fallbackResult;
                }
            } catch (Exception e) {
                log.warn("[淘宝适配器] 兜底供应商 {} 搜索异常: {}", fallbackVendorCode, e.getMessage());
            }
        }

        return result != null ? result : buildEmptyResult(request);
    }

    @Override
    public CpsPromotionLinkResult generatePromotionLink(CpsPromotionLinkRequest request) {
        CpsApiVendorClient vendor = factory.getActiveVendorClient(getPlatformCode());
        CpsVendorConfig config = factory.getActiveVendorConfig(getPlatformCode());
        if (vendor == null || config == null) {
            log.warn("[淘宝适配器] 未找到激活的供应商或配置");
            return null;
        }

        CpsVendorException primaryVendorEx = null;
        CpsPromotionLinkResult result = null;
        try {
            result = vendor.generatePromotionLink(request, config);
        } catch (CpsVendorException e) {
            primaryVendorEx = e;
            log.warn("[淘宝适配器] 主供应商 {} 转链失败: {}", vendor.getVendorCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("[淘宝适配器] 主供应商 {} 转链异常: {}", vendor.getVendorCode(), e.getMessage());
        }

        if (result != null && hasPromotionMaterial(result)) {
            return result;
        }

        // 若主供应商未返回有效推广信息（如好单库未完成淘宝授权），尝试使用备用供应商（如大淘客免授权高效转链）兜底
        String fallbackVendorCode = resolveFallbackVendorCode(vendor.getVendorCode());
        CpsApiVendorClient fallbackVendor = factory.getVendorClient(fallbackVendorCode, getPlatformCode());
        CpsVendorConfig fallbackConfig = factory.getVendorConfig(fallbackVendorCode, getPlatformCode());
        if (fallbackVendor != null && fallbackConfig != null) {
            log.info("[淘宝适配器] 主供应商 {} 转链未返回有效信息，尝试备用供应商 {} 兜底",
                    vendor.getVendorCode(), fallbackVendorCode);
            try {
                CpsPromotionLinkResult fallbackResult = fallbackVendor.generatePromotionLink(request, fallbackConfig);
                if (fallbackResult != null && hasPromotionMaterial(fallbackResult)) {
                    return fallbackResult;
                }
            } catch (Exception e) {
                log.warn("[淘宝适配器] 备用供应商 {} 转链异常: {}", fallbackVendorCode, e.getMessage());
            }
        }

        if (primaryVendorEx != null && request.isPropagateVendorError()) {
            throw primaryVendorEx;
        }
        return result;
    }

    private String resolveFallbackVendorCode(String activeVendorCode) {
        if (CpsVendorCodeEnum.DATAOKE.getCode().equals(activeVendorCode)) {
            return CpsVendorCodeEnum.HAODANKU.getCode();
        }
        if (CpsVendorCodeEnum.HAODANKU.getCode().equals(activeVendorCode)) {
            return CpsVendorCodeEnum.DATAOKE.getCode();
        }
        return CpsVendorCodeEnum.HAODANKU.getCode();
    }

    private boolean hasPromotionMaterial(CpsPromotionLinkResult result) {
        if (result == null) {
            return false;
        }
        return (result.getShortUrl() != null && !result.getShortUrl().isBlank())
                || (result.getLongUrl() != null && !result.getLongUrl().isBlank())
                || (result.getTpwd() != null && !result.getTpwd().isBlank())
                || (result.getMobileUrl() != null && !result.getMobileUrl().isBlank());
    }

    @Override
    public CpsContentParseResult parseContent(CpsContentParseRequest request) {
        CpsApiVendorClient vendor = factory.getActiveVendorClient(getPlatformCode());
        CpsVendorConfig config = factory.getActiveVendorConfig(getPlatformCode());
        if (vendor == null || config == null) {
            log.warn("[淘宝适配器] 未找到激活的供应商或配置");
            return CpsContentParseResult.unsupported("PARSE_VENDOR_UNAVAILABLE", "淘宝解析供应商暂不可用");
        }
        return vendor.parseContent(request, config);
    }

    @Override
    public CpsOrderPageResult queryOrderPage(CpsOrderQueryRequest request) {
        CpsApiVendorClient vendor = factory.getActiveVendorClient(getPlatformCode());
        CpsVendorConfig config = factory.getActiveVendorConfig(getPlatformCode());
        if (vendor == null || config == null) {
            throw CpsVendorException.unavailable(getPlatformCode());
        }
        return vendor.queryOrderPage(request, config);
    }

    @Override
    public List<CpsOrderDTO> queryOrders(CpsOrderQueryRequest request) {
        CpsApiVendorClient vendor = factory.getActiveVendorClient(getPlatformCode());
        CpsVendorConfig config = factory.getActiveVendorConfig(getPlatformCode());
        if (vendor == null || config == null) {
            log.warn("[淘宝适配器] 未找到激活的供应商或配置");
            return Collections.emptyList();
        }
        return vendor.queryOrders(request, config);
    }

    @Override
    public boolean testConnection() {
        CpsApiVendorClient vendor = factory.getActiveVendorClient(getPlatformCode());
        CpsVendorConfig config = factory.getActiveVendorConfig(getPlatformCode());
        if (vendor == null || config == null) {
            log.warn("[淘宝适配器] 未找到激活的供应商或配置");
            return false;
        }
        return vendor.testConnection(config);
    }

    private CpsGoodsSearchResult buildEmptyResult(CpsGoodsSearchRequest request) {
        return CpsGoodsSearchResult.builder()
                .list(Collections.emptyList())
                .total(0L)
                .pageNo(request.getPageNo())
                .pageSize(request.getPageSize())
                .build();
    }

}
