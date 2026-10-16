package com.qiji.cps.module.cps.controller.app.goods;

import com.qiji.cps.framework.common.pojo.CommonResult;
import com.qiji.cps.module.cps.client.dto.CpsGoodsItem;
import com.qiji.cps.module.cps.client.dto.CpsGoodsSearchRequest;
import com.qiji.cps.module.cps.client.dto.CpsGoodsSearchResult;
import com.qiji.cps.module.cps.client.dto.CpsPromotionLinkResult;
import com.qiji.cps.module.cps.controller.admin.goods.vo.CpsGoodsParseReqVO;
import com.qiji.cps.module.cps.controller.admin.goods.vo.CpsGoodsParseRespVO;
import com.qiji.cps.module.cps.controller.app.CpsAppMemberContext;
import com.qiji.cps.module.cps.controller.app.goods.vo.*;
import com.qiji.cps.module.cps.dal.dataobject.transfer.CpsTransferRecordDO;
import com.qiji.cps.module.cps.dal.mysql.transfer.CpsTransferRecordMapper;
import com.qiji.cps.module.cps.service.goods.CpsGoodsService;
import com.qiji.cps.module.cps.service.goods.CpsGoodsToolboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.qiji.cps.framework.common.pojo.CommonResult.success;

/**
 * 用户 APP - CPS商品搜索与转链
 *
 * @author CPS System
 */
@Tag(name = "用户 APP - CPS商品搜索与转链")
@RestController
@RequestMapping("/cps/goods")
@Validated
public class AppCpsGoodsController {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    @Resource
    private CpsGoodsService cpsGoodsService;
    @Resource
    private CpsGoodsToolboxService goodsToolboxService;
    @Resource
    private CpsTransferRecordMapper transferRecordMapper;

    @GetMapping("/search")
    @PermitAll
    @Operation(summary = "搜索商品（单平台）")
    public CommonResult<AppCpsGoodsSearchResult> searchGoods(@Valid AppCpsGoodsSearchReqVO reqVO) {
        if (reqVO.getPlatformCode() == null) {
            // 聚合所有平台（比价模式）
            CpsGoodsSearchRequest request = buildSearchRequest(reqVO);
            List<CpsGoodsItem> items = cpsGoodsService.searchGoodsAllPlatforms(request);
            List<AppCpsGoodsRespVO> voList = items.stream()
                    .map(this::toGoodsRespVO)
                    .collect(Collectors.toList());
            return success(AppCpsGoodsSearchResult.builder()
                    .list(voList)
                    .total((long) voList.size())
                    .build());
        } else {
            // 单平台搜索
            CpsGoodsSearchRequest request = buildSearchRequest(reqVO);
            CpsGoodsSearchResult result = cpsGoodsService.searchGoods(reqVO.getPlatformCode(), request);
            List<AppCpsGoodsRespVO> voList = result.getList().stream()
                    .map(this::toGoodsRespVO)
                    .collect(Collectors.toList());
            return success(AppCpsGoodsSearchResult.builder()
                    .list(voList)
                    .total(result.getTotal())
                    .pageNo(result.getPageNo())
                    .pageSize(result.getPageSize())
                    .nextPageId(result.getNextPageId())
                    .build());
        }
    }

    @GetMapping("/compare")
    @PermitAll
    @Operation(summary = "跨平台比价")
    public CommonResult<AppCpsGoodsCompareRespVO> compareGoods(@Valid AppCpsGoodsCompareReqVO reqVO) {
        CpsGoodsSearchRequest request = new CpsGoodsSearchRequest();
        request.setKeyword(reqVO.getKeyword());
        request.setPageNo(1);
        request.setPageSize(reqVO.getPageSize());
        request.setSortType(reqVO.getSortType());
        request.setHasCoupon(reqVO.getHasCoupon());
        List<CpsGoodsItem> items = cpsGoodsService.searchGoodsAllPlatforms(request);
        List<AppCpsGoodsRespVO> voList = items.stream()
                .map(this::toGoodsRespVO)
                .collect(Collectors.toList());
        AppCpsGoodsCompareRespVO respVO = new AppCpsGoodsCompareRespVO();
        respVO.setList(voList);
        respVO.setCheapestGoods(items.stream()
                .filter(item -> item.getActualPrice() != null)
                .min(Comparator.comparing(CpsGoodsItem::getActualPrice))
                .map(this::toGoodsRespVO)
                .orElse(null));
        respVO.setHighestRebateGoods(items.stream()
                .filter(item -> item.getCommissionAmount() != null)
                .max(Comparator.comparing(CpsGoodsItem::getCommissionAmount))
                .map(this::toGoodsRespVO)
                .orElse(null));
        respVO.setBestOverallGoods(items.stream()
                .filter(item -> item.getCommissionAmount() != null)
                .min(Comparator
                        .comparing(CpsGoodsItem::getCommissionAmount,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CpsGoodsItem::getActualPrice,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toGoodsRespVO)
                .orElse(null));
        return success(respVO);
    }

    @GetMapping("/detail")
    @PermitAll
    @Operation(summary = "查询商品详情")
    public CommonResult<AppCpsGoodsRespVO> getDetail(@Valid AppCpsGoodsDetailReqVO reqVO) {
        CpsGoodsSearchRequest request = new CpsGoodsSearchRequest();
        request.setKeyword(firstText(reqVO.getGoodsId(), reqVO.getGoodsSign()));
        request.setPageNo(1);
        request.setPageSize(1);
        CpsGoodsSearchResult result = cpsGoodsService.searchGoods(reqVO.getPlatformCode(), request);
        if (result == null || result.getList() == null || result.getList().isEmpty()) {
            return success(null);
        }
        return success(toGoodsRespVO(result.getList().get(0)));
    }

    @PostMapping("/parse")
    @PermitAll
    @Operation(summary = "解析商品链接、商品ID或口令")
    public CommonResult<AppCpsGoodsParseRespVO> parseContent(@Valid @RequestBody AppCpsGoodsParseReqVO reqVO) {
        CpsGoodsParseReqVO toolboxReqVO = new CpsGoodsParseReqVO();
        toolboxReqVO.setPlatformCode(reqVO.getPlatformCode());
        toolboxReqVO.setOriginalContent(reqVO.getOriginalContent());
        CpsGoodsParseRespVO toolboxResp = goodsToolboxService.parseContent(toolboxReqVO);
        AppCpsGoodsParseRespVO vo = toAppParseRespVO(toolboxResp);
        enrichParseRespWithGoodsDetail(vo);
        return success(vo);
    }

    @PostMapping("/link")
    @Operation(summary = "生成推广链接（转链）")
    public CommonResult<AppCpsLinkRespVO> generateLink(@Valid @RequestBody AppCpsLinkReqVO reqVO) {
        Long memberId = CpsAppMemberContext.requireMemberId();
        String adzoneId = cpsGoodsService.resolvePromotionAdzoneId(
                reqVO.getPlatformCode(), memberId, reqVO.getAdzoneId());
        CpsPromotionLinkResult result = cpsGoodsService.generatePromotionLink(
                reqVO.getPlatformCode(),
                reqVO.getGoodsId(),
                reqVO.getGoodsSign(),
                memberId,
                adzoneId,
                reqVO.getVendorCode(),
                reqVO.getOriginalContent()
        );
        if (result == null) {
            return success(null);
        }
        CpsTransferRecordDO record = CpsTransferRecordDO.builder()
                .memberId(memberId)
                .platformCode(reqVO.getPlatformCode())
                .originalContent(firstText(reqVO.getOriginalContent(), reqVO.getGoodsId(), reqVO.getGoodsSign()))
                .itemId(firstText(reqVO.getGoodsId(), reqVO.getGoodsSign()))
                .promotionUrl(firstText(result.getShortUrl(), result.getLongUrl(), result.getMobileUrl()))
                .taoCommand(result.getTpwd())
                .adzoneId(adzoneId)
                .status(1)
                .build();
        transferRecordMapper.insert(record);

        AppCpsLinkRespVO vo = new AppCpsLinkRespVO();
        vo.setPlatformCode(reqVO.getPlatformCode());
        vo.setGoodsId(reqVO.getGoodsId());
        vo.setGoodsSign(reqVO.getGoodsSign());
        vo.setShortUrl(result.getShortUrl());
        vo.setLongUrl(result.getLongUrl());
        vo.setTpwd(result.getTpwd());
        vo.setMobileUrl(result.getMobileUrl());
        vo.setPromotionUrl(firstText(result.getMobileUrl(), result.getShortUrl(), result.getLongUrl()));
        vo.setCommand(result.getTpwd());
        if (StringUtils.hasText(result.getTpwd())) {
            vo.setCommandLabel("taobao".equalsIgnoreCase(reqVO.getPlatformCode()) ? "淘口令" : "平台口令");
        }
        vo.setActualPrice(result.getActualPrice());
        vo.setCommissionRate(result.getCommissionRate());
        vo.setEstimateRebateAmount(result.getCommissionAmount());
        vo.setCouponInfo(result.getCouponInfo());
        enrichLinkRespWithGoodsDetail(vo, reqVO);
        return success(vo);
    }

    // ==================== 私有方法 ====================

    private void enrichParseRespWithGoodsDetail(AppCpsGoodsParseRespVO vo) {
        if (vo == null || !Boolean.TRUE.equals(vo.getSupported())) {
            return;
        }
        String keyword = firstText(vo.getGoodsId(), vo.getGoodsSign(), vo.getTitle());
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        try {
            CpsGoodsSearchRequest request = new CpsGoodsSearchRequest();
            request.setKeyword(keyword);
            request.setPageNo(1);
            request.setPageSize(1);
            String platform = StringUtils.hasText(vo.getPlatformCode()) ? vo.getPlatformCode() : "taobao";
            CpsGoodsSearchResult result = cpsGoodsService.searchGoods(platform, request);
            if (result != null && result.getList() != null && !result.getList().isEmpty()) {
                CpsGoodsItem item = result.getList().get(0);
                if (!StringUtils.hasText(vo.getTitle())) {
                    vo.setTitle(item.getTitle());
                }
                vo.setMainPic(item.getMainPic());
                vo.setOriginalPrice(item.getOriginalPrice());
                vo.setActualPrice(item.getActualPrice());
                vo.setCouponPrice(item.getCouponPrice());
                vo.setCouponConditions(item.getCouponConditions());
                vo.setEstimateRebateAmount(item.getCommissionAmount());
                vo.setCommissionRate(item.getCommissionRate());
                vo.setMonthSales(item.getMonthSales());
                vo.setShopName(item.getShopName());
                if (item.getCouponPrice() != null && item.getCouponPrice().compareTo(BigDecimal.ZERO) > 0) {
                    vo.setCouponInfo("券 ¥" + item.getCouponPrice().stripTrailingZeros().toPlainString());
                }
            }
        } catch (Exception ignored) {
            // 安全降级，不阻断基础解析
        }
    }

    private void enrichLinkRespWithGoodsDetail(AppCpsLinkRespVO vo, AppCpsLinkReqVO reqVO) {
        if (vo.getCouponPrice() == null && StringUtils.hasText(vo.getCouponInfo())) {
            BigDecimal parsed = extractCouponAmount(vo.getCouponInfo());
            if (parsed != null) {
                vo.setCouponPrice(parsed);
            }
        }
        String keyword = firstText(reqVO.getGoodsId(), reqVO.getGoodsSign());
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        try {
            CpsGoodsSearchRequest request = new CpsGoodsSearchRequest();
            request.setKeyword(keyword);
            request.setPageNo(1);
            request.setPageSize(1);
            CpsGoodsSearchResult result = cpsGoodsService.searchGoods(reqVO.getPlatformCode(), request);
            if (result != null && result.getList() != null && !result.getList().isEmpty()) {
                CpsGoodsItem item = result.getList().get(0);
                if (!StringUtils.hasText(vo.getTitle())) {
                    vo.setTitle(item.getTitle());
                }
                if (!StringUtils.hasText(vo.getMainPic())) {
                    vo.setMainPic(item.getMainPic());
                }
                if (vo.getOriginalPrice() == null) {
                    vo.setOriginalPrice(item.getOriginalPrice());
                }
                if (vo.getActualPrice() == null) {
                    vo.setActualPrice(item.getActualPrice());
                }
                if (vo.getCouponPrice() == null) {
                    vo.setCouponPrice(item.getCouponPrice());
                }
                if (vo.getEstimateRebateAmount() == null) {
                    vo.setEstimateRebateAmount(item.getCommissionAmount());
                }
                if (vo.getCommissionRate() == null) {
                    vo.setCommissionRate(item.getCommissionRate());
                }
            }
        } catch (Exception ignored) {
            // 安全降级
        }
    }

    private BigDecimal extractCouponAmount(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return new BigDecimal(matcher.group(1));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private CpsGoodsSearchRequest buildSearchRequest(AppCpsGoodsSearchReqVO reqVO) {
        CpsGoodsSearchRequest request = new CpsGoodsSearchRequest();
        request.setKeyword(reqVO.getKeyword());
        request.setPageNo(reqVO.getPageNo());
        request.setPageSize(reqVO.getPageSize());
        request.setSortType(reqVO.getSortType());
        request.setPriceLowerLimit(reqVO.getPriceLowerLimit());
        request.setPriceUpperLimit(reqVO.getPriceUpperLimit());
        request.setHasCoupon(reqVO.getHasCoupon());
        return request;
    }

    private AppCpsGoodsRespVO toGoodsRespVO(CpsGoodsItem item) {
        AppCpsGoodsRespVO vo = new AppCpsGoodsRespVO();
        vo.setGoodsId(item.getGoodsId());
        vo.setGoodsSign(item.getGoodsSign());
        vo.setPlatformCode(item.getPlatformCode());
        vo.setTitle(item.getTitle());
        vo.setMainPic(item.getMainPic());
        vo.setOriginalPrice(item.getOriginalPrice());
        vo.setActualPrice(item.getActualPrice());
        vo.setCouponPrice(item.getCouponPrice());
        vo.setCouponConditions(item.getCouponConditions());
        vo.setCouponTotalNum(item.getCouponTotalNum());
        vo.setCouponRemainNum(item.getCouponRemainNum());
        vo.setCouponReceiveNum(item.getCouponReceiveNum());
        vo.setCouponStartTime(item.getCouponStartTime());
        vo.setCouponEndTime(item.getCouponEndTime());
        vo.setEstimateRebateAmount(item.getCommissionAmount());
        vo.setCommissionRate(item.getCommissionRate());
        vo.setMonthSales(item.getMonthSales());
        vo.setShopName(item.getShopName());
        vo.setShopType(item.getShopType());
        vo.setItemLink(item.getItemLink());
        vo.setBrandName(item.getBrandName());
        vo.setVendorCode(item.getVendorCode());
        vo.setSource(item.getSource());
        vo.setActivityTag(item.getActivityTag());
        vo.setCategoryName(item.getCategoryName());
        vo.setRankTag(item.getRankTag());
        vo.setSellingPoint(item.getSellingPoint());
        return vo;
    }

    private AppCpsGoodsParseRespVO toAppParseRespVO(CpsGoodsParseRespVO source) {
        AppCpsGoodsParseRespVO vo = new AppCpsGoodsParseRespVO();
        vo.setPlatformCode(source.getPlatformCode());
        vo.setSupported(source.getSupported());
        vo.setGoodsId(source.getGoodsId());
        vo.setGoodsSign(source.getGoodsSign());
        vo.setItemLink(source.getItemLink());
        vo.setCouponLink(source.getCouponLink());
        vo.setSourceLink(source.getSourceLink());
        vo.setTitle(source.getTitle());
        vo.setParseSource(source.getParseSource());
        vo.setFailureCode(source.getFailureCode());
        vo.setFailureReason(source.getFailureReason());
        return vo;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

}
