package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.dto.order.IncomingOrder;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.enums.Platform;
import com.vip.adp.api.open.service.GetByGoodsIdsRequest;
import com.vip.adp.api.open.service.GoodsInfo;
import com.vip.adp.api.open.service.OrderQueryModel;
import com.vip.adp.api.open.service.QueryGoodsRequest;
import com.vip.adp.api.open.service.UnionGoodsV2ServiceHelper;
import com.vip.adp.api.open.service.UnionOrderV2ServiceHelper;
import com.vip.adp.api.open.service.UnionUrlV2ServiceHelper;
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
 * 唯品会联盟。只用商品查询、按商品号详情、按商品号转链和订单列表。传输走 osp-sdk。
 */
@Component
public class VipGoodsClient implements PlatformGoodsClient {

    private static final String GATEWAY = "https://vop.vipapis.com/";

    private final String appKey;
    private final String appSecret;

    public VipGoodsClient(
            @Value("${UNION_VIP_APP_KEY:}") String appKey,
            @Value("${UNION_VIP_APP_SECRET:}") String appSecret) {
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    @Override
    public String platform() {
        return "vip";
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
        Upstream.call(appKey, appSecret);
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        try {
            QueryGoodsRequest request = new QueryGoodsRequest();
            request.setKeyword(keyword);
            request.setPage(1);
            request.setPageSize(20);
            bind(request, promotionId);
            var response = SdkLog.exchange("vip", "goods.query", request, () -> goodsClient().query(request));
            if (response == null) {
                throw new PlatformCallException();
            }
            if (response.getGoodsInfoList() == null || response.getGoodsInfoList().isEmpty()) {
                return List.of();
            }
            List<GoodsSnapshot> found = new ArrayList<>();
            for (GoodsInfo row : response.getGoodsInfoList()) {
                GoodsSnapshot snapshot = snapshot(row);
                if (snapshot != null) {
                    found.add(snapshot);
                }
            }
            return found;
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    @Override
    public PromotionLink convert(GoodsSnapshot goods, boolean openInApp, String userId, String promotionId) {
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
            return new PromotionLink(url, deeplink, UnionMaps.http(info.getUlUrl()), UnionMaps.first(info.getLongUrl(), url));
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    @Override
    public List<IncomingOrder> pullOrders(OrderSyncCursor cursor) {
        Upstream.call(appKey, appSecret);
        try {
            UnionOrderV2ServiceHelper.UnionOrderV2ServiceClient http = orderClient();
            Map<String, IncomingOrder> merged = new LinkedHashMap<>();
            pullAxis(http, true, UnionMaps.window(cursor == null ? null : cursor.getLastSyncCreateTime()), merged);
            pullAxis(http, false, UnionMaps.window(cursor == null ? null : cursor.getLastSyncUpdateTime()), merged);
            return List.copyOf(merged.values());
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
            Map<String, IncomingOrder> merged) throws Exception {
        for (int page = 1; page <= 3; page++) {
            OrderQueryModel request = new OrderQueryModel();
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
                throw new PlatformCallException();
            }
            var rows = response.getOrderInfoList();
            if (rows == null || rows.isEmpty()) {
                return;
            }
            for (var row : rows) {
                IncomingOrder order = order(row);
                if (order != null) {
                    merged.put(order.platformOrderId(), order);
                }
            }
            if (rows.size() < 50) {
                return;
            }
        }
    }

    private UnionGoodsV2ServiceHelper.UnionGoodsV2ServiceClient goodsClient() {
        var client = new UnionGoodsV2ServiceHelper.UnionGoodsV2ServiceClient();
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

    private static IncomingOrder order(com.vip.adp.api.open.service.OrderInfo row) {
        if (row.getOrderSn() == null || row.getOrderSn().isBlank()) {
            return null;
        }
        String status = UnionMaps.vipStatus(row.getStatus());
        String goodsId = null;
        String title = null;
        String image = null;
        if (row.getDetailList() != null && !row.getDetailList().isEmpty()) {
            var detail = row.getDetailList().get(0);
            goodsId = detail.getGoodsId();
            title = detail.getGoodsName();
            image = UnionMaps.http(detail.getGoodsThumb());
        }
        return new IncomingOrder(
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
                "shipped".equals(status) || "completed".equals(status) ? UnionMaps.fromEpoch(row.getSignTime()) : null);
    }
}
