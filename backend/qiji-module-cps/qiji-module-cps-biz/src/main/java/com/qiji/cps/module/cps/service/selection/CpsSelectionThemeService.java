package com.qiji.cps.module.cps.service.selection;

import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.module.cps.controller.admin.selection.vo.*;
import com.qiji.cps.module.cps.dal.dataobject.selection.CpsSelectionAiReviewDO;
import com.qiji.cps.module.cps.dal.dataobject.selection.CpsSelectionThemeDO;
import com.qiji.cps.module.cps.dal.dataobject.selection.CpsSelectionThemeItemDO;
import jakarta.validation.Valid;

import java.util.List;

public interface CpsSelectionThemeService {

    Long createTheme(@Valid CpsSelectionThemeSaveReqVO createReqVO);

    void updateTheme(@Valid CpsSelectionThemeSaveReqVO updateReqVO);

    void deleteTheme(Long id);

    void deleteThemeList(List<Long> ids);

    void publishTheme(Long id);

    void offlineTheme(Long id);

    CpsSelectionThemeDO getTheme(Long id);

    PageResult<CpsSelectionThemeDO> getThemePage(CpsSelectionThemePageReqVO pageReqVO);

    CpsSelectionThemeStatsRespVO getThemeStats(CpsSelectionThemePageReqVO pageReqVO);

    List<CpsSelectionThemeDO> listPublishedThemes(String keyword, String promotionEvent);

    List<CpsSelectionThemeItemDO> listItems(Long themeId);

    PageResult<CpsSelectionThemeItemDO> getItemPage(CpsSelectionThemeItemPageReqVO pageReqVO);

    List<CpsSelectionThemeItemDO> listEnabledItems(Long themeId);

    int importItems(@Valid CpsSelectionThemeItemImportReqVO reqVO);

    void updateItemSort(@Valid CpsSelectionThemeItemSortReqVO reqVO);

    void updateItemStatus(@Valid CpsSelectionThemeItemStatusReqVO reqVO);

    void deleteItem(Long id);

    CpsSelectionThemeOperationRespVO vendorPull(@Valid CpsSelectionThemeVendorPullReqVO reqVO);

    CpsSelectionThemeOperationRespVO syncVendorThemes(@Valid CpsSelectionThemeSyncReqVO reqVO);

    CpsSelectionThemeOperationRespVO syncDataokeThemes(@Valid CpsSelectionThemeSyncReqVO reqVO);

    CpsSelectionThemeOperationRespVO aiRecommend(@Valid CpsSelectionThemeAiRecommendReqVO reqVO);

    List<CpsSelectionThemeTemplateRespVO> listPromotionTemplates();

    Long createFromTemplate(@Valid CpsSelectionThemeTemplateCreateReqVO reqVO);

    CpsSelectionThemeOperationRespVO refreshAiSavedFilter(Long id);

    CpsSelectionThemeOperationRespVO refreshAiSavedFilters();

    List<CpsSelectionAiReviewDO> listAiReviews(String reviewContextId, Long ownerUserId);

    Long upsertAiReview(@Valid CpsSelectionAiReviewReqVO reqVO, Long reviewerId);

    /**
     * 更新单个选品商品的商品价格趋势跟踪开关
     */
    void updateItemPriceTrackStatus(@Valid CpsSelectionThemeItemPriceTrackReqVO reqVO);

    /**
     * 批量更新选品商品的商品价格趋势跟踪开关
     */
    void batchUpdateItemPriceTrackStatus(@Valid CpsSelectionThemeItemPriceTrackBatchReqVO reqVO);

    /**
     * 获取商品价格趋势详情
     */
    CpsGoodsPriceTrendRespVO getGoodsPriceTrend(Long selectionItemId, String platformCode, String goodsId);

    /**
     * 手动触发单个选品商品的价格拉取
     */
    CpsGoodsPricePointVO pullItemPrice(Long selectionItemId);

    /**
     * 定时任务定期拉取已开启价格跟踪的选品商品价格
     */
    String trackTrackedItemPrices(Integer batchSize);
}
