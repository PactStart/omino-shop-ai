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
import com.omino.shopai.support.Ids;
import com.vip.adp.api.open.service.CheckUserRequest;
import com.vip.adp.api.open.service.CheckUserResponse;
import com.vip.adp.api.open.service.GetByGoodsIdsRequest;
import com.vip.adp.api.open.service.GetChannelUrlByTypeData;
import com.vip.adp.api.open.service.GetChannelUrlByTypeEnum;
import com.vip.adp.api.open.service.GetChannelUrlByTypeRequest;
import com.vip.adp.api.open.service.GetChannelUrlByTypeResponse;
import com.vip.adp.api.open.service.GoodsInfo;
import com.vip.adp.api.open.service.GoodsInfoResponse;
import com.vip.adp.api.open.service.OrderQueryModel;
import com.vip.adp.api.open.service.QueryGoodsRequest;
import com.vip.adp.api.open.service.SimilarRecommendRequest;
import com.vip.adp.api.open.service.UserRecommendRequest;
import com.vip.adp.api.open.service.VipLinkCheckReq;
import com.vip.adp.api.open.service.VipLinkCheckResp;
import com.vip.adp.api.open.service.UnBindOpenIdResponse;
import com.vip.adp.api.open.service.UnbindOpenIdRequest;
import com.vip.adp.api.open.service.UnionGoodsV2ServiceHelper;
import com.vip.adp.api.open.service.UnionOrderV2ServiceHelper;
import com.vip.adp.api.open.service.UnionUrlV2ServiceHelper;
import com.vip.adp.api.open.service.UnionUserV2ServiceHelper;
import com.vip.adp.api.open.service.UrlGenByGoodsIdRequest;
import com.vip.adp.api.open.service.UrlInfo;
import com.vip.osp.sdk.context.ClientInvocationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 唯品会联盟。商品查询、详情、转链、订单、授权和备案。另接相似、猜你喜欢、链接解析，口令取转链里的 onlyCommand。
 */
@Component
public class VipGoodsImpl implements IPlatformGoods {

    private static final String GATEWAY = "https://vop.vipapis.com/";

    private final String appKey;
    private final String appSecret;

    public VipGoodsImpl(
            @Value("${UNION_VIP_APP_KEY:}") String appKey,
            @Value("${UNION_VIP_APP_SECRET:}") String appSecret) {
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    /**
     * 这个实现负责唯品会。
     *
     * @return 唯品会
     */
    @Override
    public Platform platform() {
        return Platform.VIP;
    }

    @Override
    public GoodsSnapshot detail(String platformGoodsId, String userId, String promotionId) {
        Upstream.call(appKey, appSecret);
        if (platformGoodsId == null || platformGoodsId.isBlank()) {
            throw new PlatformCallException();
        }
        try {
            GetByGoodsIdsRequest request = new GetByGoodsIdsRequest();
            request.setGoodsIds(List.of(platformGoodsId));
            request.setQueryDetail(true);
            bind(request, promotionId);
            var rows = SdkLog.exchange("vip", "goods.byIds", request, () -> goodsClient().getByGoodsIdsV2(request));
            if (rows == null || rows.isEmpty()) {
                return null;
            }
            return snapshot(rows.get(0));
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
            QueryGoodsRequest request = new QueryGoodsRequest();
            request.setKeyword(query.keyword());
            request.setPage(Math.max(query.page() == null ? 1 : query.page(), 1));
            request.setPageSize(clamp(query.size() == null ? 20 : query.size()));
            applySort(request, query.sort());
            if (query.priceMin() != null) {
                request.setPriceStart(query.priceMin());
            }
            if (query.priceMax() != null) {
                request.setPriceEnd(query.priceMax());
            }
            if (Boolean.TRUE.equals(query.coupon())) {
                request.setQueryExclusiveCoupon(true);
            }
            bind(request, promotionId);
            GoodsInfoResponse response = SdkLog.exchange("vip", "goods.query", request, () -> goodsClient().query(request));
            return pageOf(response);
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
        String goodsId = goods == null ? null : goods.getPlatformGoodsId();
        if (goodsId == null || goodsId.isBlank()) {
            throw new PlatformCallException();
        }
        try {
            String buyer = UnionMaps.userId(userId);
            UrlGenByGoodsIdRequest request = new UrlGenByGoodsIdRequest();
            bind(request, promotionId);
            var response = SdkLog.exchange("vip", "url.command", goodsId, () -> urlClient().genByGoodsId(
                    List.of(goodsId),
                    "default_pid",
                    Ids.ulid(),
                    buyer == null ? "" : buyer,
                    Boolean.FALSE,
                    Boolean.FALSE,
                    Boolean.TRUE,
                    request));
            if (response == null || response.getUrlInfoList() == null || response.getUrlInfoList().isEmpty()) {
                throw new PlatformCallException();
            }
            return response.getUrlInfoList().get(0).getOnlyCommand();
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
            VipLinkCheckReq request = new VipLinkCheckReq();
            request.setContent(content.trim());
            request.setRequestId(Ids.ulid());
            VipLinkCheckResp response = SdkLog.exchange(
                    "vip", "url.check", request, () -> urlClient().vipLinkCheck(request));
            if (response == null || response.getSuccessMap() == null || response.getSuccessMap().isEmpty()) {
                throw new PlatformCallException();
            }
            var checked = response.getSuccessMap().values().iterator().next();
            String goodsId = checked.getGoodsId();
            GoodsSnapshot goods = goodsId == null || goodsId.isBlank() ? null : detail(goodsId, userId, promotionId);
            return new ParsedMaterial(goods, UnionMaps.http(checked.getLandUrl()));
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
        Upstream.call(appKey, appSecret);
        if (platformGoodsId == null || platformGoodsId.isBlank()) {
            return GoodsHitPage.empty();
        }
        try {
            SimilarRecommendRequest request = new SimilarRecommendRequest();
            request.setGoodsId(platformGoodsId);
            request.setPage(Math.max(page, 1));
            request.setPageSize(clamp(size));
            request.setRequestId(Ids.ulid());
            request.setChanTag("default_pid");
            request.setOpenId(UnionMaps.promotion(promotionId));
            GoodsInfoResponse response = SdkLog.exchange(
                    "vip", "goods.similar", request, () -> goodsClient().similarRecommend(request));
            return pageOf(response);
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    @Override
    public boolean offersGuess() {
        return true;
    }

    @Override
    public GoodsHitPage guess(int page, int size, String userId, String promotionId) {
        Upstream.call(appKey, appSecret);
        try {
            UserRecommendRequest request = new UserRecommendRequest();
            request.setPage(Math.max(page, 1));
            request.setPageSize(clamp(size));
            request.setRequestId(Ids.ulid());
            request.setChanTag("default_pid");
            request.setOpenId(UnionMaps.promotion(promotionId));
            GoodsInfoResponse response = SdkLog.exchange(
                    "vip", "goods.guess", request, () -> goodsClient().userRecommend(request));
            return pageOf(response);
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    @Override
    public PromotionLinkDto convert(GoodsSnapshot goods, boolean openInApp, String userId, String promotionId) {
        Upstream.call(appKey, appSecret);
        String goodsId = goods == null ? null : goods.getPlatformGoodsId();
        if (goodsId == null || goodsId.isBlank()) {
            throw new PlatformCallException();
        }
        try {
            String buyer = UnionMaps.userId(userId);
            UrlGenByGoodsIdRequest request = new UrlGenByGoodsIdRequest();
            bind(request, promotionId);
            var response = SdkLog.exchange("vip", "url.byGoodsId", goodsId, () -> urlClient().genByGoodsId(
                    List.of(goodsId),
                    "default_pid",
                    "req",
                    buyer == null ? "" : buyer,
                    Boolean.FALSE,
                    Boolean.FALSE,
                    Boolean.TRUE,
                    request));
            if (response == null || response.getUrlInfoList() == null || response.getUrlInfoList().isEmpty()) {
                throw new PlatformCallException();
            }
            UrlInfo info = response.getUrlInfoList().get(0);
            String url = UnionMaps.first(info.getUrl(), info.getLongUrl(), info.getUlUrl());
            if (url == null) {
                throw new PlatformCallException();
            }
            String deeplink = openInApp ? UnionMaps.http(info.getDeeplinkUrl()) : null;
            return new PromotionLinkDto(url, deeplink, UnionMaps.http(info.getUlUrl()), UnionMaps.first(info.getLongUrl(), url));
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    /**
     * 按下单、更新各拉一段。空段跳过。每次查询都带请求号。页拉完才返回。
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
            UnionOrderV2ServiceHelper.UnionOrderV2ServiceClient http = orderClient();
            Map<String, IncomingOrderDto> merged = new LinkedHashMap<>();
            if (created != null) {
                pullAxis(http, true, created, merged);
            }
            if (updated != null) {
                pullAxis(http, false, updated, merged);
            }
            return List.copyOf(merged.values());
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException(ex);
        }
    }

    /**
     * 问这个 openId 是否已在渠道授权。结果为 1 才算已授权。
     *
     * @param openId 记在本表里的唯品会身份
     * @return 已授权时为真
     */
    public boolean bound(String openId) {
        Upstream.call(appKey, appSecret);
        try {
            CheckUserRequest request = new CheckUserRequest();
            request.setOpenId(UnionMaps.promotion(openId));
            request.setRequestId(Ids.ulid());
            request.setScene(2);
            UnionUserV2ServiceHelper.UnionUserV2ServiceClient http = userClient();
            CheckUserResponse response = SdkLog.exchange("vip", "user.check", request, () -> http.checkUser(request));
            if (response == null || response.getResult() == null) {
                throw new PlatformCallException();
            }
            return response.getResult() == 1;
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    /**
     * 取备案链接。chanTag 仍是固定推广位，身份写在 openId。
     *
     * @param userId 当前用户，写进统计参数，便于订单回到本人
     * @param openId 记在本表里的唯品会身份
     * @return 给客户端打开的一条链接
     */
    public String authUrl(String userId, String openId) {
        Upstream.call(appKey, appSecret);
        try {
            GetChannelUrlByTypeRequest request = new GetChannelUrlByTypeRequest();
            request.setType(GetChannelUrlByTypeEnum.BIND_FILING_LINK);
            request.setChanTag("default_pid");
            request.setOpenId(UnionMaps.promotion(openId));
            request.setRequestId(Ids.ulid());
            request.setRealCall(true);
            String buyer = UnionMaps.userId(userId);
            if (buyer != null) {
                request.setStatParam(buyer);
            }
            UnionUrlV2ServiceHelper.UnionUrlV2ServiceClient http = urlClient();
            GetChannelUrlByTypeResponse response = SdkLog.exchange(
                    "vip", "channel.url", request, () -> http.getChannelUrlByType(request));
            if (response == null || response.getData() == null) {
                throw new PlatformCallException();
            }
            GetChannelUrlByTypeData data = response.getData();
            String url = UnionMaps.first(data.getDeeplinkUrl(), data.getShortUrl(), data.getShortLink(), data.getVipWxUrl());
            if (url == null) {
                throw new PlatformCallException();
            }
            return url;
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    /**
     * 解除这个 openId 的授权。调用失败时不改本地状态。
     *
     * @param openId 记在本表里的唯品会身份
     */
    public void unbind(String openId) {
        Upstream.call(appKey, appSecret);
        try {
            UnbindOpenIdRequest request = new UnbindOpenIdRequest();
            request.setOpenId(UnionMaps.promotion(openId));
            request.setRequestId(Ids.ulid());
            UnionUserV2ServiceHelper.UnionUserV2ServiceClient http = userClient();
            UnBindOpenIdResponse response = SdkLog.exchange("vip", "user.unbind", request, () -> http.unbindOpenId(request));
            if (response == null) {
                throw new PlatformCallException();
            }
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    /** 推广身份写 openId。chanTag 仍是固定推广位。 */
    public static void bind(QueryGoodsRequest request, String openId) {
        request.setChanTag("default_pid");
        request.setOpenId(UnionMaps.promotion(openId));
    }

    /** 详情与搜品使用同一对身份。 */
    public static void bind(GetByGoodsIdsRequest request, String openId) {
        request.setChanTag("default_pid");
        request.setOpenId(UnionMaps.promotion(openId));
    }

    /** 转链的 chanTag 在方法参数里，这里只写 openId。 */
    public static void bind(UrlGenByGoodsIdRequest request, String openId) {
        request.setOpenId(UnionMaps.promotion(openId));
    }

    private void pullAxis(
            UnionOrderV2ServiceHelper.UnionOrderV2ServiceClient http,
            boolean byCreate,
            UnionMaps.Window window,
            Map<String, IncomingOrderDto> merged) throws Exception {
        boolean finished = false;
        for (int page = 1; page <= UnionMaps.MAX_ORDER_PAGES; page++) {
            OrderQueryModel request = new OrderQueryModel();
            request.setRequestId(Ids.ulid());
            request.setPage(page);
            request.setPageSize(50);
            request.setChanTag("default_pid");
            if (byCreate) {
                request.setOrderTimeStart(window.start().toInstant().toEpochMilli());
                request.setOrderTimeEnd(window.end().toInstant().toEpochMilli());
            } else {
                request.setUpdateTimeStart(window.start().toInstant().toEpochMilli());
                request.setUpdateTimeEnd(window.end().toInstant().toEpochMilli());
            }
            var response = SdkLog.exchange("vip", "order.list", request, () -> http.orderList(request));
            if (response == null) {
                throw new PlatformCallException("唯品会订单响应为空");
            }
            var rows = response.getOrderInfoList();
            if (rows != null) {
                for (var row : rows) {
                    IncomingOrderDto order = order(row);
                    if (order != null) {
                        merged.put(order.platformOrderId(), order);
                    }
                }
            }
            if (rows == null || rows.size() < 50) {
                finished = true;
                break;
            }
        }
        if (!finished) {
            throw new PlatformCallException("本段订单超过 40 页，水位不前进");
        }
    }

    private GoodsHitPage pageOf(GoodsInfoResponse response) {
        if (response == null) {
            throw new PlatformCallException();
        }
        List<GoodsSnapshot> found = new ArrayList<>();
        if (response.getGoodsInfoList() != null) {
            for (GoodsInfo row : response.getGoodsInfoList()) {
                GoodsSnapshot snapshot = snapshot(row);
                if (snapshot != null) {
                    found.add(snapshot);
                }
            }
        }
        Integer total = response.getTotal();
        return new GoodsHitPage(found, total == null ? found.size() : total);
    }

    private static void applySort(QueryGoodsRequest request, String sort) {
        if (sort == null) {
            return;
        }
        switch (sort) {
            case "sales" -> {
                request.setFieldName("SALES");
                request.setOrder(1);
            }
            case "price" -> {
                request.setFieldName("PRICE");
                request.setOrder(0);
            }
            case "commission" -> {
                request.setFieldName("COMMISSION");
                request.setOrder(1);
            }
            case "coupon" -> {
                request.setFieldName("DISCOUNT");
                request.setOrder(1);
            }
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

    private UnionGoodsV2ServiceHelper.UnionGoodsV2ServiceClient goodsClient() {
        var client = new UnionGoodsV2ServiceHelper.UnionGoodsV2ServiceClient();
        client.setClientInvocationContext(context());
        return client;
    }

    /**
     * 授权检查和解绑走用户服务，不走商品服务。
     *
     * @return 带本应用密钥的客户端
     */
    private UnionUserV2ServiceHelper.UnionUserV2ServiceClient userClient() {
        var client = new UnionUserV2ServiceHelper.UnionUserV2ServiceClient();
        client.setClientInvocationContext(context());
        return client;
    }

    private UnionUrlV2ServiceHelper.UnionUrlV2ServiceClient urlClient() {
        var client = new UnionUrlV2ServiceHelper.UnionUrlV2ServiceClient();
        client.setClientInvocationContext(context());
        return client;
    }

    private UnionOrderV2ServiceHelper.UnionOrderV2ServiceClient orderClient() {
        var client = new UnionOrderV2ServiceHelper.UnionOrderV2ServiceClient();
        client.setClientInvocationContext(context());
        return client;
    }

    private ClientInvocationContext context() {
        ClientInvocationContext context = new ClientInvocationContext();
        context.setAppKey(appKey);
        context.setAppSecret(appSecret);
        context.setAppURL(GATEWAY);
        context.setLanguage("zh");
        context.setConnectTimeOut(UnionMaps.TIMEOUT_MS);
        context.setReadTimeOut(UnionMaps.TIMEOUT_MS);
        SdkSessions.markOpened();
        return context;
    }

    private static GoodsSnapshot snapshot(GoodsInfo row) {
        if (row.getGoodsId() == null || row.getGoodsId().isBlank()) {
            return null;
        }
        GoodsSnapshot snapshot = new GoodsSnapshot();
        snapshot.setPlatform(Platform.VIP);
        snapshot.setPlatformGoodsId(row.getGoodsId());
        snapshot.setTitle(row.getGoodsName());
        snapshot.setImageUrl(UnionMaps.first(row.getGoodsThumbUrl(), row.getGoodsMainPicture()));
        snapshot.setShopName(row.getBrandName());
        snapshot.setGoodsUrl(UnionMaps.http(row.getDestUrl()));
        snapshot.setPrice(UnionMaps.money(row.getMarketPrice()));
        snapshot.setCouponPrice(UnionMaps.money(row.getVipPrice()));
        snapshot.setSalesCount(CountMaps.sales(row.getProductSales()));
        snapshot.setReviewCount(row.getCommentsInfo() == null ? null : CountMaps.reviews(row.getCommentsInfo().getComments()));
        snapshot.setUpdatedAt(OffsetDateTime.now(UnionMaps.SHANGHAI));
        return snapshot;
    }

    private static IncomingOrderDto order(com.vip.adp.api.open.service.OrderInfo row) {
        if (row.getOrderSn() == null || row.getOrderSn().isBlank()) {
            return null;
        }
        OrderStatus status = UnionMaps.vipStatus(row.getStatus());
        String goodsId = null;
        String title = null;
        String image = null;
        if (row.getDetailList() != null && !row.getDetailList().isEmpty()) {
            var detail = row.getDetailList().get(0);
            goodsId = detail.getGoodsId();
            title = detail.getGoodsName();
            image = UnionMaps.http(detail.getGoodsThumb());
        }
        return new IncomingOrderDto(
                row.getOrderSn(),
                UnionMaps.userId(row.getStatParam()),
                null,
                goodsId,
                title,
                image,
                UnionMaps.money(row.getTotalCost()),
                UnionMaps.money(row.getCommission()),
                status,
                UnionMaps.fromEpoch(row.getOrderTime()),
                status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED ? UnionMaps.fromEpoch(row.getSignTime()) : null,
                null,
                status == OrderStatus.COMPLETED ? UnionMaps.fromEpoch(row.getSignTime()) : null,
                null);
    }
}
