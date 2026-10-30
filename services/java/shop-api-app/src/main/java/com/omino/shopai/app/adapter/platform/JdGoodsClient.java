package com.omino.shopai.app.adapter.platform;

import com.jd.open.api.sdk.DefaultJdClient;
import com.jd.open.api.sdk.domain.kplunion.GoodsService.request.query.GoodsReq;
import com.jd.open.api.sdk.domain.kplunion.GoodsService.response.query.GoodsResp;
import com.jd.open.api.sdk.domain.kplunion.GoodsService.response.query.UrlInfo;
import com.jd.open.api.sdk.domain.kplunion.OrderService.request.query.OrderRowReq;
import com.jd.open.api.sdk.domain.kplunion.OrderService.response.query.OrderRowResp;
import com.jd.open.api.sdk.domain.kplunion.promotionbysubunioni.PromotionService.request.get.PromotionCodeReq;
import com.jd.open.api.sdk.request.kplunion.UnionOpenGoodsQueryRequest;
import com.jd.open.api.sdk.request.kplunion.UnionOpenOrderRowQueryRequest;
import com.jd.open.api.sdk.request.kplunion.UnionOpenPromotionBysubunionidGetRequest;
import com.jd.open.api.sdk.response.kplunion.UnionOpenGoodsQueryResponse;
import com.jd.open.api.sdk.response.kplunion.UnionOpenOrderRowQueryResponse;
import com.jd.open.api.sdk.response.kplunion.UnionOpenPromotionBysubunionidGetResponse;
import com.omino.shopai.dto.order.IncomingOrder;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.enums.Platform;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 京东联盟。只用商品查询、子联盟转链和订单行。
 */
@Component
public class JdGoodsClient implements PlatformGoodsClient {

    private static final String GATEWAY = "https://api.jd.com/routerjson";

    private final String appKey;
    private final String appSecret;

    public JdGoodsClient(
            @Value("${UNION_JD_APP_KEY:}") String appKey,
            @Value("${UNION_JD_APP_SECRET:}") String appSecret) {
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    @Override
    public String platform() {
        return "jd";
    }

    @Override
    public GoodsSnapshot detail(String platformGoodsId) {
        Upstream.call(appKey, appSecret);
        if (platformGoodsId == null || platformGoodsId.isBlank()) {
            throw new PlatformCallException();
        }
        try {
            GoodsReq req = new GoodsReq();
            req.setSceneId(1);
            req.setPageIndex(1);
            req.setPageSize(1);
            if (platformGoodsId.chars().allMatch(Character::isDigit)) {
                req.setSkuIds(new Long[] {Long.parseLong(platformGoodsId)});
            } else {
                req.setItemIds(new String[] {platformGoodsId});
            }
            GoodsResp[] rows = query(req);
            if (rows.length == 0) {
                return null;
            }
            return snapshot(rows[0]);
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    @Override
    public List<GoodsSnapshot> search(String keyword) {
        Upstream.call(appKey, appSecret);
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        try {
            GoodsReq req = new GoodsReq();
            req.setSceneId(1);
            req.setKeyword(keyword);
            req.setPageIndex(1);
            req.setPageSize(20);
            GoodsResp[] rows = query(req);
            List<GoodsSnapshot> found = new ArrayList<>();
            for (GoodsResp row : rows) {
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
    public PromotionLink convert(GoodsSnapshot goods, boolean openInApp, String userId) {
        Upstream.call(appKey, appSecret);
        String itemId = goods == null ? null : goods.getPlatformGoodsId();
        if (itemId == null || itemId.isBlank()) {
            throw new PlatformCallException();
        }
        try {
            PromotionCodeReq req = new PromotionCodeReq();
            String material = goods.getGoodsUrl();
            if (material == null || material.isBlank()) {
                material = "https://item.jd.com/" + itemId + ".html";
            }
            req.setMaterialId(material);
            req.setSceneId(1);
            String buyer = UnionMaps.userId(userId);
            if (buyer != null) {
                req.setSubUnionId(buyer);
            }
            UnionOpenPromotionBysubunionidGetRequest request = new UnionOpenPromotionBysubunionidGetRequest();
            request.setPromotionCodeReq(req);
            UnionOpenPromotionBysubunionidGetResponse response = SdkLog.exchange(
                    "jd", "promotion.bysubunionid", request, () -> client().execute(request));
            if (response == null || !"0".equals(response.getCode()) || response.getGetResult() == null
                    || response.getGetResult().getCode() != 200 || response.getGetResult().getData() == null) {
                throw new PlatformCallException();
            }
            var data = response.getGetResult().getData();
            String url = UnionMaps.first(data.getClickURL(), data.getShortURL());
            if (url == null) {
                throw new PlatformCallException();
            }
            String deeplink = openInApp && url.startsWith("openapp.") ? url : null;
            return new PromotionLink(url, deeplink, null, url);
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
            DefaultJdClient http = client();
            Map<String, IncomingOrder> merged = new LinkedHashMap<>();
            pullAxis(http, 1, UnionMaps.window(cursor == null ? null : cursor.getLastSyncCreateTime()), merged);
            pullAxis(http, 3, UnionMaps.window(cursor == null ? null : cursor.getLastSyncUpdateTime()), merged);
            return List.copyOf(merged.values());
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    private GoodsResp[] query(GoodsReq req) throws Exception {
        UnionOpenGoodsQueryRequest request = new UnionOpenGoodsQueryRequest();
        request.setGoodsReqDTO(req);
        UnionOpenGoodsQueryResponse response = SdkLog.exchange(
                "jd", "goods.query", request, () -> client().execute(request));
        if (response == null || !"0".equals(response.getCode()) || response.getQueryResult() == null
                || response.getQueryResult().getCode() != 200) {
            throw new PlatformCallException();
        }
        GoodsResp[] data = response.getQueryResult().getData();
        return data == null ? new GoodsResp[0] : data;
    }

    private void pullAxis(DefaultJdClient http, int type, UnionMaps.Window window, Map<String, IncomingOrder> merged)
            throws Exception {
        for (int page = 1; page <= 3; page++) {
            OrderRowReq req = new OrderRowReq();
            req.setType(type);
            req.setStartTime(UnionMaps.format(window.start()));
            req.setEndTime(UnionMaps.format(window.end()));
            req.setPageIndex(page);
            req.setPageSize(50);
            UnionOpenOrderRowQueryRequest request = new UnionOpenOrderRowQueryRequest();
            request.setOrderReq(req);
            UnionOpenOrderRowQueryResponse response = SdkLog.exchange(
                    "jd", "order.row", request, () -> http.execute(request));
            if (response == null || !"0".equals(response.getCode()) || response.getQueryResult() == null
                    || response.getQueryResult().getCode() != 200) {
                throw new PlatformCallException();
            }
            OrderRowResp[] rows = response.getQueryResult().getData();
            if (rows == null || rows.length == 0) {
                return;
            }
            for (OrderRowResp row : rows) {
                IncomingOrder order = order(row);
                if (order != null) {
                    merged.put(order.platformOrderId(), order);
                }
            }
            if (rows.length < 50 || !Boolean.TRUE.equals(response.getQueryResult().getHasMore())) {
                return;
            }
        }
    }

    private DefaultJdClient client() {
        SdkSessions.markOpened();
        return new DefaultJdClient(GATEWAY, null, appKey, appSecret, UnionMaps.TIMEOUT_MS, UnionMaps.TIMEOUT_MS);
    }

    private static GoodsSnapshot snapshot(GoodsResp row) {
        String id = row.getItemId();
        if (id == null || id.isBlank()) {
            id = row.getSkuId() == null ? null : row.getSkuId().toString();
        }
        if (id == null || id.isBlank()) {
            return null;
        }
        GoodsSnapshot snapshot = new GoodsSnapshot();
        snapshot.setPlatform(Platform.JD);
        snapshot.setPlatformGoodsId(id);
        snapshot.setTitle(row.getSkuName());
        snapshot.setSalesCount(CountMaps.sales(row.getInOrderCount30Days()));
        snapshot.setReviewCount(CountMaps.reviews(row.getComments()));
        snapshot.setGoodsUrl(UnionMaps.http(row.getMaterialUrl()));
        snapshot.setUpdatedAt(OffsetDateTime.now(UnionMaps.SHANGHAI));
        if (row.getPriceInfo() != null) {
            snapshot.setPrice(UnionMaps.money(row.getPriceInfo().getPrice()));
            snapshot.setCouponPrice(UnionMaps.money(row.getPriceInfo().getLowestCouponPrice()));
        }
        if (row.getShopInfo() != null) {
            snapshot.setShopName(row.getShopInfo().getShopName());
        }
        if (row.getImageInfo() != null && row.getImageInfo().getImageList() != null
                && row.getImageInfo().getImageList().length > 0) {
            UrlInfo image = row.getImageInfo().getImageList()[0];
            snapshot.setImageUrl(image == null ? null : UnionMaps.http(image.getUrl()));
        }
        return snapshot;
    }

    private static IncomingOrder order(OrderRowResp row) {
        if (row.getOrderId() == null) {
            return null;
        }
        String status = UnionMaps.jdStatus(row.getValidCode());
        String image = row.getGoodsInfo() == null ? null : UnionMaps.http(row.getGoodsInfo().getImageUrl());
        String goodsId = row.getItemId();
        if (goodsId == null || goodsId.isBlank()) {
            goodsId = row.getSkuId() == null ? null : row.getSkuId().toString();
        }
        return new IncomingOrder(
                row.getId() == null || row.getId().isBlank() ? row.getOrderId().toString() : row.getId(),
                UnionMaps.userId(row.getSubUnionId()),
                null,
                goodsId,
                row.getSkuName(),
                image,
                UnionMaps.money(row.getEstimateCosPrice()),
                UnionMaps.money(row.getEstimateFee()),
                status,
                UnionMaps.parseTime(row.getOrderTime()),
                "completed".equals(status) ? UnionMaps.parseTime(row.getFinishTime()) : null);
    }
}
