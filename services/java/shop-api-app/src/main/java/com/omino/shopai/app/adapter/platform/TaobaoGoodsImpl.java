package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.app.util.SdkSessions;

import com.omino.shopai.app.util.Upstream;

import com.omino.shopai.app.util.SdkLog;

import com.omino.shopai.app.util.CountMaps;

import com.omino.shopai.app.util.UnionMaps;

import com.omino.shopai.app.dto.GoodsHitPage;
import com.omino.shopai.app.dto.GoodsSearchRequestDto;
import com.omino.shopai.app.dto.ParsedMaterial;
import com.omino.shopai.app.dto.PromotionLinkDto;

import com.omino.shopai.exception.PlatformCallException;

import com.omino.shopai.dto.order.IncomingOrderDto;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.enums.OrderStatus;
import com.omino.shopai.enums.Platform;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import com.taobao.api.AutoRetryTaobaoClient;
import com.taobao.api.request.TbkDgGeneralLinkConvertRequest;
import com.taobao.api.request.TbkDgMaterialOptionalUpgradeRequest;
import com.taobao.api.request.TbkDgMaterialRecommendRequest;
import com.taobao.api.request.TbkItemInfoUpgradeGetRequest;
import com.taobao.api.request.TbkOrderDetailsGetRequest;
import com.taobao.api.request.TbkScPublisherInfoSaveRequest;
import com.taobao.api.request.TbkTpwdCreateRequest;
import com.taobao.api.request.TopAuthTokenCreateRequest;
import com.taobao.api.response.TbkDgGeneralLinkConvertResponse;
import com.taobao.api.response.TbkDgMaterialOptionalUpgradeResponse;
import com.taobao.api.response.TbkDgMaterialRecommendResponse;
import com.taobao.api.response.TbkItemInfoUpgradeGetResponse;
import com.taobao.api.response.TbkOrderDetailsGetResponse;
import com.taobao.api.response.TbkScPublisherInfoSaveResponse;
import com.taobao.api.response.TbkTpwdCreateResponse;
import com.taobao.api.response.TopAuthTokenCreateResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 淘宝客。物料搜索、商品详情、万能转链、订单明细、授权换票和渠道备案。口令、相似和猜你喜欢另走对应官方接口。
 */
@Component
public class TaobaoGoodsImpl implements IPlatformGoods {

    private static final String GATEWAY = "https://eco.taobao.com/router/rest";
    private static final long MATERIAL_ID = 6707L;
    /** 官方物料：按商品出相似推荐。 */
    private static final long SIMILAR_MATERIAL = 13256L;
    /** 官方物料：渠道每日实时热销，用作猜你喜欢。 */
    private static final long GUESS_MATERIAL = 28026L;
    /** 搜品和转链的广告位。与参考客户端的默认值相同，不从环境变量读。 */
    private static final long ADZONE_ID = 110216900017L;

    private final String appKey;
    private final String appSecret;
    private final String inviteCode;
    private final String oauthRedirect;

    /**
     * 测试只给应用标识和密钥。备案邀请码和回调地址留空，授权链接不会出站。
     *
     * @param appKey 应用标识
     * @param appSecret 应用密钥
     */
    public TaobaoGoodsImpl(String appKey, String appSecret) {
        this(appKey, appSecret, "", "");
    }

    /**
     * 淘宝客客户端。邀请码和回调地址只用于授权，不参与搜品。
     *
     * @param appKey 应用标识
     * @param appSecret 应用密钥
     * @param inviteCode 渠道备案邀请码
     * @param oauthRedirect 已在开放平台登记的回调地址
     */
    @Autowired
    public TaobaoGoodsImpl(
            @Value("${UNION_TAOBAO_APP_KEY:}") String appKey,
            @Value("${UNION_TAOBAO_APP_SECRET:}") String appSecret,
            @Value("${UNION_TAOBAO_INVITE_CODE:}") String inviteCode,
            @Value("${UNION_TAOBAO_OAUTH_REDIRECT:}") String oauthRedirect) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.inviteCode = inviteCode;
        this.oauthRedirect = oauthRedirect;
    }

    /**
     * 这个实现负责淘宝。
     *
     * @return 淘宝
     */
    @Override
    public Platform platform() {
        return Platform.TAOBAO;
    }

    @Override
    public GoodsSnapshot detail(String platformGoodsId, String userId, String promotionId) {
        Upstream.call(appKey, appSecret);
        if (platformGoodsId == null || platformGoodsId.isBlank()) {
            throw new PlatformCallException();
        }
        try {
            TbkItemInfoUpgradeGetRequest request = new TbkItemInfoUpgradeGetRequest();
            request.setItemId(platformGoodsId);
            request.setRelationId(UnionMaps.promotion(promotionId));
            TbkItemInfoUpgradeGetResponse response = SdkLog.exchange(
                    "taobao", "item.info", request, () -> client().execute(request));
            if (response == null || !response.isSuccess()) {
                throw new PlatformCallException();
            }
            List<TbkItemInfoUpgradeGetResponse.TbkItemDetail> results = response.getResults();
            if (results == null || results.isEmpty()) {
                return null;
            }
            return detailSnapshot(results.get(0));
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    @Override
    public List<GoodsSnapshot> search(String keyword, String userId, String promotionId) {
        return searchPage(GoodsSearchRequestDto.firstPage(platform().code(), keyword), userId, promotionId).list();
    }

    @Override
    public GoodsHitPage searchPage(GoodsSearchRequestDto query, String userId, String promotionId) {
        Upstream.call(appKey, appSecret);
        if (query == null || query.keyword() == null || query.keyword().isBlank()) {
            return GoodsHitPage.empty();
        }
        try {
            TbkDgMaterialOptionalUpgradeRequest request = new TbkDgMaterialOptionalUpgradeRequest();
            request.setQ(query.keyword());
            request.setAdzoneId(ADZONE_ID);
            request.setSpecialId(UnionMaps.promotion(promotionId));
            request.setMaterialId(MATERIAL_ID);
            request.setPageNo((long) Math.max(query.page() == null ? 1 : query.page(), 1));
            request.setPageSize((long) clamp(query.size() == null ? 20 : query.size()));
            applySort(request, query.sort());
            if (query.priceMin() != null) {
                request.setStartPrice(GoodsSearchRequestDto.yuanWhole(query.priceMin()));
            }
            if (query.priceMax() != null) {
                request.setEndPrice(GoodsSearchRequestDto.yuanWhole(query.priceMax()));
            }
            if (Boolean.TRUE.equals(query.coupon())) {
                request.setHasCoupon(true);
            }
            TbkDgMaterialOptionalUpgradeResponse response = SdkLog.exchange(
                    "taobao", "material.optional", request, () -> client().execute(request));
            if (response == null || !response.isSuccess()) {
                throw new PlatformCallException();
            }
            List<GoodsSnapshot> found = new ArrayList<>();
            List<TbkDgMaterialOptionalUpgradeResponse.MapData> rows = response.getResultList();
            if (rows != null) {
                for (TbkDgMaterialOptionalUpgradeResponse.MapData row : rows) {
                    GoodsSnapshot snapshot = searchSnapshot(row);
                    if (snapshot != null) {
                        found.add(snapshot);
                    }
                }
            }
            long total = response.getTotalResults() == null ? found.size() : response.getTotalResults();
            return new GoodsHitPage(found, total);
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    @Override
    public boolean offersCommand() {
        return true;
    }

    @Override
    public String command(GoodsSnapshot goods, String userId, String promotionId) {
        Upstream.call(appKey, appSecret);
        String itemId = goods == null ? null : goods.getPlatformGoodsId();
        if (itemId == null || itemId.isBlank()) {
            throw new PlatformCallException();
        }
        try {
            TbkDgGeneralLinkConvertResponse.ItemLinkInfoDTO info = itemLink(itemId, promotionId);
            String ready = UnionMaps.first(info.getCpsShortTpwd(), info.getCpsFullTpwd(), info.getCouponShortTpwd());
            if (ready != null) {
                return ready;
            }
            String url = UnionMaps.first(info.getCpsShortUrl(), info.getCpsLongUrl(), info.getCouponShortUrl());
            if (url == null) {
                throw new PlatformCallException();
            }
            TbkTpwdCreateRequest request = new TbkTpwdCreateRequest();
            request.setUrl(url);
            request.setText(goods.getTitle() == null || goods.getTitle().isBlank() ? "商品" : goods.getTitle());
            TbkTpwdCreateResponse response = SdkLog.exchange(
                    "taobao", "tpwd.create", request, () -> client().execute(request));
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new PlatformCallException();
            }
            return UnionMaps.first(response.getData().getPasswordSimple(), response.getData().getModel());
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    @Override
    public boolean offersParse() {
        return true;
    }

    @Override
    public ParsedMaterial parse(String content, String userId, String promotionId) {
        Upstream.call(appKey, appSecret);
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            TbkDgGeneralLinkConvertRequest request = new TbkDgGeneralLinkConvertRequest();
            request.setAdzoneId(ADZONE_ID);
            request.setRelationId(UnionMaps.promotion(promotionId));
            request.setMaterialList(content.trim());
            TbkDgGeneralLinkConvertResponse response = SdkLog.exchange(
                    "taobao", "general.link.parse", request, () -> client().execute(request));
            if (response == null || !response.isSuccess() || response.getData() == null
                    || response.getData().getMaterialUrlList() == null
                    || response.getData().getMaterialUrlList().isEmpty()) {
                throw new PlatformCallException();
            }
            TbkDgGeneralLinkConvertResponse.MaterialUrlList row = response.getData().getMaterialUrlList().get(0);
            if (row.getCode() != null && row.getCode() != 0L) {
                throw new PlatformCallException();
            }
            TbkDgGeneralLinkConvertResponse.LinkInfoDTO info = row.getLinkInfoDto();
            String url = info == null ? null : UnionMaps.first(
                    info.getCpsShortUrl(), info.getCpsLongUrl(), info.getCouponShortUrl(), info.getCouponLongUrl());
            String itemId = itemId(row.getExtraInfo());
            GoodsSnapshot goods = itemId == null ? null : detail(itemId, userId, promotionId);
            return new ParsedMaterial(goods, url);
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    @Override
    public boolean offersSimilar() {
        return true;
    }

    @Override
    public GoodsHitPage similar(String platformGoodsId, int page, int size, String userId, String promotionId) {
        return recommend(SIMILAR_MATERIAL, platformGoodsId, page, size, promotionId);
    }

    @Override
    public boolean offersGuess() {
        return true;
    }

    @Override
    public GoodsHitPage guess(int page, int size, String userId, String promotionId) {
        return recommend(GUESS_MATERIAL, null, page, size, promotionId);
    }

    @Override
    public PromotionLinkDto convert(GoodsSnapshot goods, boolean openInApp, String userId, String promotionId) {
        Upstream.call(appKey, appSecret);
        String itemId = goods == null ? null : goods.getPlatformGoodsId();
        if (itemId == null || itemId.isBlank()) {
            throw new PlatformCallException();
        }
        try {
            TbkDgGeneralLinkConvertResponse.ItemLinkInfoDTO info = itemLink(itemId, promotionId);
            String url = UnionMaps.first(info.getCpsShortUrl(), info.getCpsLongUrl(), info.getCouponShortUrl(), info.getCouponLongUrl());
            if (url == null) {
                throw new PlatformCallException();
            }
            return new PromotionLinkDto(url, null, null, url);
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    /**
     * 按下单、更新各拉一段。空段跳过。页拉完才返回，页还满则本段失败。
     *
     * @param cursor 当前游标
     * @param created 下单时间这一段
     * @param updated 更新时间这一段
     * @return 这两段里的订单
     */
    @Override
    public List<IncomingOrderDto> pullOrders(OrderSyncCursor cursor, UnionMaps.Window created, UnionMaps.Window updated) {
        Upstream.call(appKey, appSecret);
        try {
            AutoRetryTaobaoClient http = client();
            Map<String, IncomingOrderDto> merged = new LinkedHashMap<>();
            if (created != null) {
                pullAxis(http, 1L, created, merged);
            }
            if (updated != null) {
                pullAxis(http, 4L, updated, merged);
            }
            return List.copyOf(merged.values());
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException(ex);
        }
    }

    private void pullAxis(AutoRetryTaobaoClient http, long queryType, UnionMaps.Window window, Map<String, IncomingOrderDto> merged)
            throws Exception {
        boolean finished = false;
        for (long page = 1; page <= UnionMaps.MAX_ORDER_PAGES; page++) {
            TbkOrderDetailsGetRequest request = new TbkOrderDetailsGetRequest();
            request.setQueryType(queryType);
            request.setStartTime(UnionMaps.format(window.start()));
            request.setEndTime(UnionMaps.format(window.end()));
            request.setPageNo(page);
            request.setPageSize(50L);
            request.setOrderScene(1L);
            TbkOrderDetailsGetResponse response = SdkLog.exchange(
                    "taobao", "order.details", request, () -> http.execute(request));
            if (response == null || !response.isSuccess()) {
                throw new PlatformCallException(response == null
                        ? "淘宝订单响应为空"
                        : PlatformCallException.explain(response.getSubCode(), response.getSubMsg(), response.getMsg()));
            }
            List<TbkOrderDetailsGetResponse.PublisherOrderDto> rows = response.getData() == null
                    ? List.of()
                    : response.getData().getResults();
            if (rows == null || rows.isEmpty() || rows.size() < 50) {
                if (rows != null) {
                    for (TbkOrderDetailsGetResponse.PublisherOrderDto row : rows) {
                        IncomingOrderDto order = order(row);
                        if (order != null) {
                            merged.put(order.platformOrderId(), order);
                        }
                    }
                }
                finished = true;
                break;
            }
            for (TbkOrderDetailsGetResponse.PublisherOrderDto row : rows) {
                IncomingOrderDto order = order(row);
                if (order != null) {
                    merged.put(order.platformOrderId(), order);
                }
            }
        }
        if (!finished) {
            throw new PlatformCallException("本段订单超过 40 页，水位不前进");
        }
    }

    /**
     * 拼出手机页授权地址。缺应用标识或回调地址时不出站。
     *
     * @param state 已签名的状态，回调时再验
     * @return 淘宝授权页地址
     */
    public String authorizeUrl(String state) {
        if (appKey == null || appKey.isBlank()
                || oauthRedirect == null || oauthRedirect.isBlank()
                || state == null || state.isBlank()) {
            throw new PlatformCallException();
        }
        return "https://oauth.taobao.com/authorize?response_type=code&client_id="
                + URLEncoder.encode(appKey, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(oauthRedirect, StandardCharsets.UTF_8)
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
                + "&view=wap";
    }

    /**
     * 用一次性授权码换票，再做渠道备案。成功时返回要写入本表的推广身份。
     *
     * @param code 淘宝回调里的授权码
     * @return relationId；没有 relationId 时用 specialId
     */
    public String filing(String code) {
        Upstream.call(appKey, appSecret);
        if (inviteCode == null || inviteCode.isBlank() || code == null || code.isBlank()) {
            throw new PlatformCallException();
        }
        try {
            TopAuthTokenCreateRequest tokenRequest = new TopAuthTokenCreateRequest();
            tokenRequest.setCode(code);
            TopAuthTokenCreateResponse tokenResponse = SdkLog.exchange(
                    "taobao", "auth.token", tokenRequest, () -> client().execute(tokenRequest));
            if (tokenResponse == null || !tokenResponse.isSuccess() || tokenResponse.getTokenResult() == null) {
                throw new PlatformCallException();
            }
            // 会话只用于紧接着的备案，不落库，也不放进响应。
            String session = new ObjectMapper().readTree(tokenResponse.getTokenResult()).path("access_token").asText("");
            if (session.isBlank()) {
                throw new PlatformCallException();
            }
            TbkScPublisherInfoSaveRequest save = new TbkScPublisherInfoSaveRequest();
            save.setInviterCode(inviteCode);
            save.setInfoType(1L);
            TbkScPublisherInfoSaveResponse saved = SdkLog.exchange(
                    "taobao", "publisher.info.save", save, () -> client().execute(save, session));
            if (saved == null || !saved.isSuccess() || saved.getData() == null) {
                throw new PlatformCallException();
            }
            TbkScPublisherInfoSaveResponse.Data data = saved.getData();
            Long relation = data.getRelationId() != null ? data.getRelationId() : data.getSpecialId();
            if (relation == null) {
                throw new PlatformCallException();
            }
            return relation.toString();
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    private TbkDgGeneralLinkConvertResponse.ItemLinkInfoDTO itemLink(String itemId, String promotionId) throws Exception {
        TbkDgGeneralLinkConvertRequest request = new TbkDgGeneralLinkConvertRequest();
        request.setAdzoneId(ADZONE_ID);
        request.setRelationId(UnionMaps.promotion(promotionId));
        request.setItemIdList(itemId);
        TbkDgGeneralLinkConvertResponse response = SdkLog.exchange(
                "taobao", "general.link.convert", request, () -> client().execute(request));
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new PlatformCallException();
        }
        List<TbkDgGeneralLinkConvertResponse.ItemUrlList> items = response.getData().getItemUrlList();
        if (items == null || items.isEmpty() || items.get(0).getLinkInfoDto() == null) {
            throw new PlatformCallException();
        }
        TbkDgGeneralLinkConvertResponse.ItemUrlList item = items.get(0);
        if (item.getCode() != null && item.getCode() != 0L) {
            throw new PlatformCallException();
        }
        return item.getLinkInfoDto();
    }

    private GoodsHitPage recommend(long materialId, String itemId, int page, int size, String promotionId) {
        Upstream.call(appKey, appSecret);
        try {
            TbkDgMaterialRecommendRequest request = new TbkDgMaterialRecommendRequest();
            request.setAdzoneId(ADZONE_ID);
            request.setMaterialId(materialId);
            request.setPageNo((long) Math.max(page, 1));
            request.setPageSize((long) clamp(size));
            if (itemId != null && !itemId.isBlank()) {
                request.setItemId(itemId);
            }
            Long relation = relationId(promotionId);
            if (relation != null) {
                request.setRelationId(relation);
            }
            TbkDgMaterialRecommendResponse response = SdkLog.exchange(
                    "taobao", "material.recommend", request, () -> client().execute(request));
            if (response == null || !response.isSuccess()) {
                throw new PlatformCallException();
            }
            List<GoodsSnapshot> found = new ArrayList<>();
            if (response.getResultList() != null) {
                for (TbkDgMaterialRecommendResponse.MapData row : response.getResultList()) {
                    GoodsSnapshot snapshot = recommendSnapshot(row);
                    if (snapshot != null) {
                        found.add(snapshot);
                    }
                }
            }
            long total = response.getTotalCount() == null ? found.size() : response.getTotalCount();
            return new GoodsHitPage(found, total);
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    private static void applySort(TbkDgMaterialOptionalUpgradeRequest request, String sort) {
        if (sort == null) {
            return;
        }
        switch (sort) {
            case "sales" -> request.setSort("total_sales_des");
            case "price" -> request.setSort("final_promotion_price_asc");
            case "commission" -> request.setSort("tk_rate_des");
            case "coupon" -> request.setHasCoupon(true);
            default -> {
            }
        }
    }

    private static int clamp(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 20);
    }

    private static Long relationId(String promotionId) {
        String value = UnionMaps.promotion(promotionId);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String itemId(String extra) {
        if (extra == null || extra.isBlank()) {
            return null;
        }
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(extra);
            String id = node.path("itemId").asText("");
            if (id.isBlank()) {
                id = node.path("item_id").asText("");
            }
            return id.isBlank() ? null : id;
        } catch (Exception ex) {
            return null;
        }
    }

    private static GoodsSnapshot recommendSnapshot(TbkDgMaterialRecommendResponse.MapData row) {
        if (row.getItemId() == null || row.getItemId().isBlank()) {
            return null;
        }
        GoodsSnapshot snapshot = base(row.getItemId());
        TbkDgMaterialRecommendResponse.BasicMapData basic = row.getItemBasicInfo();
        if (basic != null) {
            snapshot.setTitle(basic.getTitle());
            snapshot.setImageUrl(UnionMaps.http(basic.getPictUrl()));
            snapshot.setShopName(basic.getShopTitle());
            snapshot.setSalesCount(basic.getVolume() != null
                    ? CountMaps.sales(basic.getVolume())
                    : CountMaps.sales(basic.getAnnualVol()));
        }
        TbkDgMaterialRecommendResponse.PromotionInfoMapData price = row.getPricePromotionInfo();
        if (price != null) {
            snapshot.setPrice(UnionMaps.money(price.getReservePrice()));
            snapshot.setCouponPrice(UnionMaps.money(price.getZkFinalPrice()));
        }
        return snapshot;
    }

    private AutoRetryTaobaoClient client() {
        AutoRetryTaobaoClient http = new AutoRetryTaobaoClient(GATEWAY, appKey, appSecret);
        http.setConnectTimeout(UnionMaps.TIMEOUT_MS);
        http.setReadTimeout(UnionMaps.TIMEOUT_MS);
        http.setMaxRetryCount(0);
        http.setNeedEnableLogger(false);
        SdkSessions.markOpened();
        return http;
    }

    private static GoodsSnapshot searchSnapshot(TbkDgMaterialOptionalUpgradeResponse.MapData row) {
        if (row.getItemId() == null || row.getItemId().isBlank()) {
            return null;
        }
        GoodsSnapshot snapshot = base(row.getItemId());
        TbkDgMaterialOptionalUpgradeResponse.BasicMapData basic = row.getItemBasicInfo();
        if (basic != null) {
            snapshot.setTitle(basic.getTitle());
            snapshot.setImageUrl(UnionMaps.http(basic.getPictUrl()));
            snapshot.setShopName(basic.getShopTitle());
            snapshot.setSalesCount(basic.getVolume() != null
                    ? CountMaps.sales(basic.getVolume())
                    : CountMaps.sales(basic.getAnnualVol()));
        }
        TbkDgMaterialOptionalUpgradeResponse.PromotionInfoMapData price = row.getPricePromotionInfo();
        if (price != null) {
            snapshot.setPrice(UnionMaps.money(price.getReservePrice()));
            snapshot.setCouponPrice(UnionMaps.money(price.getZkFinalPrice()));
        }
        return snapshot;
    }

    private static GoodsSnapshot detailSnapshot(TbkItemInfoUpgradeGetResponse.TbkItemDetail row) {
        if (row.getItemId() == null || row.getItemId().isBlank()) {
            return null;
        }
        GoodsSnapshot snapshot = base(row.getItemId());
        TbkItemInfoUpgradeGetResponse.BasicMapData basic = row.getItemBasicInfo();
        if (basic != null) {
            snapshot.setTitle(basic.getTitle());
            snapshot.setImageUrl(UnionMaps.http(basic.getPictUrl()));
            snapshot.setShopName(basic.getShopTitle());
            snapshot.setGoodsUrl(UnionMaps.http(basic.getItemUrl()));
            snapshot.setSalesCount(basic.getVolume() != null
                    ? CountMaps.sales(basic.getVolume())
                    : CountMaps.sales(basic.getAnnualVol()));
        }
        TbkItemInfoUpgradeGetResponse.PromotionInfoMapData price = row.getPricePromotionInfo();
        if (price != null) {
            snapshot.setPrice(UnionMaps.money(price.getReservePrice()));
            snapshot.setCouponPrice(UnionMaps.money(price.getZkFinalPrice()));
        }
        return snapshot;
    }

    private static GoodsSnapshot base(String itemId) {
        GoodsSnapshot snapshot = new GoodsSnapshot();
        snapshot.setPlatform(Platform.TAOBAO);
        snapshot.setPlatformGoodsId(itemId);
        snapshot.setUpdatedAt(OffsetDateTime.now(UnionMaps.SHANGHAI));
        return snapshot;
    }

    private static IncomingOrderDto order(TbkOrderDetailsGetResponse.PublisherOrderDto row) {
        String id = row.getTradeId();
        if (id == null || id.isBlank()) {
            return null;
        }
        OrderStatus status = UnionMaps.taobaoStatus(row.getTkStatus());
        OffsetDateTime orderedAt = UnionMaps.parseTime(row.getTkCreateTime());
        OffsetDateTime shippedAt = status == OrderStatus.COMPLETED ? UnionMaps.parseTime(row.getTkEarningTime()) : null;
        OffsetDateTime paidAt = UnionMaps.parseTime(row.getTkPaidTime());
        if (paidAt == null) {
            paidAt = UnionMaps.parseTime(row.getTbPaidTime());
        }
        return new IncomingOrderDto(
                id,
                null,
                null,
                row.getItemId(),
                row.getItemTitle(),
                UnionMaps.http(row.getItemImg()),
                amount(row.getPayPrice(), row.getAlipayTotalPrice()),
                amount(row.getPubShareFee(), row.getPubSharePreFee()),
                status,
                orderedAt,
                shippedAt,
                paidAt,
                null,
                null,
                row.getSpecialId() == null ? null : row.getSpecialId().toString());
    }

    /**
     * 已付款但未结算时，淘宝的实付和结算佣金经常还是空或 0，金额在备用字段里。
     *
     * @param primary 平台主金额
     * @param fallback 主金额还没有数时的备用金额
     * @return 元，两位小数；都没有则为空
     */
    private static BigDecimal amount(String primary, String fallback) {
        BigDecimal main = UnionMaps.money(primary);
        if (main != null && main.signum() != 0) {
            return main;
        }
        BigDecimal spare = UnionMaps.money(fallback);
        if (spare != null && spare.signum() != 0) {
            return spare;
        }
        return main != null ? main : spare;
    }
}
