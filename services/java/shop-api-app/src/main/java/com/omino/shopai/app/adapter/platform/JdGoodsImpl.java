package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.app.util.SdkSessions;

import com.omino.shopai.app.util.Upstream;

import com.omino.shopai.app.util.SdkLog;

import com.omino.shopai.app.util.CountMaps;

import com.omino.shopai.app.util.UnionMaps;

import com.omino.shopai.app.dto.GoodsHitPage;
import com.omino.shopai.app.dto.GoodsSearchRequestDto;
import com.omino.shopai.app.dto.PromotionLinkDto;

import com.omino.shopai.exception.PlatformCallException;

import com.jd.open.api.sdk.DefaultJdClient;
import com.jd.open.api.sdk.domain.kplunion.GoodsService.request.query.GoodsReq;
import com.jd.open.api.sdk.domain.kplunion.GoodsService.request.query.MaterialGoodsReq;
import com.jd.open.api.sdk.domain.kplunion.GoodsService.response.query.GoodsResp;
import com.jd.open.api.sdk.domain.kplunion.GoodsService.response.query.MaterialGoodsResp;
import com.jd.open.api.sdk.domain.kplunion.GoodsService.response.query.UrlInfo;
import com.jd.open.api.sdk.domain.kplunion.OrderService.request.query.OrderRowReq;
import com.jd.open.api.sdk.domain.kplunion.OrderService.response.query.OrderRowResp;
import com.jd.open.api.sdk.domain.kplunion.promotionbysubunioni.PromotionService.request.get.PromotionCodeReq;
import com.jd.open.api.sdk.request.kplunion.UnionOpenGoodsMaterialQueryRequest;
import com.jd.open.api.sdk.request.kplunion.UnionOpenGoodsQueryRequest;
import com.jd.open.api.sdk.request.kplunion.UnionOpenOrderRowQueryRequest;
import com.jd.open.api.sdk.request.kplunion.UnionOpenPromotionBysubunionidGetRequest;
import com.jd.open.api.sdk.response.kplunion.UnionOpenGoodsMaterialQueryResponse;
import com.jd.open.api.sdk.response.kplunion.UnionOpenGoodsQueryResponse;
import com.jd.open.api.sdk.response.kplunion.UnionOpenOrderRowQueryResponse;
import com.jd.open.api.sdk.response.kplunion.UnionOpenPromotionBysubunionidGetResponse;
import com.omino.shopai.dto.order.IncomingOrderDto;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.enums.OrderStatus;
import com.omino.shopai.enums.Platform;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 京东联盟。商品查询、子联盟转链和订单行。口令随转链返回，猜你喜欢走物料查询。没有相似商品接口。
 */
@Component
public class JdGoodsImpl implements IPlatformGoods {

    private static final String GATEWAY = "https://api.jd.com/routerjson";

    private final String appKey;
    private final String appSecret;

    public JdGoodsImpl(
            @Value("${UNION_JD_APP_KEY:}") String appKey,
            @Value("${UNION_JD_APP_SECRET:}") String appSecret) {
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    /**
     * 这个实现负责京东。
     *
     * @return 京东
     */
    @Override
    public Platform platform() {
        return Platform.JD;
    }

    @Override
    public GoodsSnapshot detail(String platformGoodsId, String userId, String promotionId) {
        Upstream.call(appKey, appSecret);
        if (platformGoodsId == null || platformGoodsId.isBlank()) {
            throw new PlatformCallException();
        }
        try {
            GoodsReq req = new GoodsReq();
            req.setPid(UnionMaps.promotion(promotionId));
            req.setSceneId(1);
            req.setPageIndex(1);
            req.setPageSize(1);
            if (platformGoodsId.chars().allMatch(Character::isDigit)) {
                req.setSkuIds(new Long[] {Long.parseLong(platformGoodsId)});
            } else {
                req.setItemIds(new String[] {platformGoodsId});
            }
            GoodsResp[] rows = query(req).rows();
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
            GoodsReq req = new GoodsReq();
            req.setPid(UnionMaps.promotion(promotionId));
            req.setSceneId(1);
            req.setKeyword(query.keyword());
            req.setPageIndex(Math.max(query.page() == null ? 1 : query.page(), 1));
            req.setPageSize(clamp(query.size() == null ? 20 : query.size()));
            applySort(req, query.sort());
            if (query.priceMin() != null) {
                req.setPricefrom(GoodsSearchRequestDto.yuan(query.priceMin()));
            }
            if (query.priceMax() != null) {
                req.setPriceto(GoodsSearchRequestDto.yuan(query.priceMax()));
            }
            if (Boolean.TRUE.equals(query.coupon())) {
                req.setIsCoupon(1);
            }
            if (query.nextId() != null && !query.nextId().isBlank()) {
                req.setSearchPosition(query.nextId());
            }
            QueryBatch batch = query(req);
            List<GoodsSnapshot> found = new ArrayList<>();
            for (GoodsResp row : batch.rows()) {
                GoodsSnapshot snapshot = snapshot(row);
                if (snapshot != null) {
                    found.add(snapshot);
                }
            }
            return new GoodsHitPage(found, batch.total(), batch.nextId());
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
            PromotionCodeReq req = promotion(goods, userId, promotionId);
            req.setCommand(1);
            UnionOpenPromotionBysubunionidGetRequest request = new UnionOpenPromotionBysubunionidGetRequest();
            request.setPromotionCodeReq(req);
            UnionOpenPromotionBysubunionidGetResponse response = SdkLog.exchange(
                    "jd", "promotion.command", request, () -> client().execute(request));
            if (response == null || !"0".equals(response.getCode()) || response.getGetResult() == null
                    || response.getGetResult().getCode() != 200 || response.getGetResult().getData() == null) {
                throw new PlatformCallException();
            }
            var data = response.getGetResult().getData();
            return UnionMaps.first(data.getJShortCommand(), data.getJCommand());
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
            MaterialGoodsReq req = new MaterialGoodsReq();
            req.setEliteId(1);
            req.setPageIndex(Math.max(page, 1));
            req.setPageSize(clamp(size));
            req.setPid(UnionMaps.promotion(promotionId));
            String buyer = UnionMaps.userId(userId);
            if (buyer != null) {
                req.setSubUnionId(buyer);
            }
            UnionOpenGoodsMaterialQueryRequest request = new UnionOpenGoodsMaterialQueryRequest();
            request.setGoodsReq(req);
            UnionOpenGoodsMaterialQueryResponse response = SdkLog.exchange(
                    "jd", "goods.material", request, () -> client().execute(request));
            if (response == null || !"0".equals(response.getCode()) || response.getQueryResult() == null
                    || response.getQueryResult().getCode() != 200) {
                throw new PlatformCallException();
            }
            MaterialGoodsResp[] rows = response.getQueryResult().getData();
            List<GoodsSnapshot> found = new ArrayList<>();
            if (rows != null) {
                for (MaterialGoodsResp row : rows) {
                    GoodsSnapshot snapshot = materialSnapshot(row);
                    if (snapshot != null) {
                        found.add(snapshot);
                    }
                }
            }
            return new GoodsHitPage(found, response.getQueryResult().getTotalCount());
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    @Override
    public PromotionLinkDto convert(GoodsSnapshot goods, boolean openInApp, String userId, String promotionId) {
        Upstream.call(appKey, appSecret);
        String itemId = goods == null ? null : goods.getPlatformGoodsId();
        if (itemId == null || itemId.isBlank()) {
            throw new PlatformCallException();
        }
        try {
            PromotionCodeReq req = promotion(goods, userId, promotionId);
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
            return new PromotionLinkDto(url, deeplink, null, url);
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
            DefaultJdClient http = client();
            Map<String, IncomingOrderDto> merged = new LinkedHashMap<>();
            if (created != null) {
                pullAxis(http, 1, created, merged);
            }
            if (updated != null) {
                pullAxis(http, 3, updated, merged);
            }
            return List.copyOf(merged.values());
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException(ex);
        }
    }

    private QueryBatch query(GoodsReq req) throws Exception {
        UnionOpenGoodsQueryRequest request = new UnionOpenGoodsQueryRequest();
        request.setGoodsReqDTO(req);
        UnionOpenGoodsQueryResponse response = SdkLog.exchange(
                "jd", "goods.query", request, () -> client().execute(request));
        if (response == null || !"0".equals(response.getCode()) || response.getQueryResult() == null
                || response.getQueryResult().getCode() != 200) {
            throw new PlatformCallException();
        }
        GoodsResp[] data = response.getQueryResult().getData();
        return new QueryBatch(
                data == null ? new GoodsResp[0] : data,
                response.getQueryResult().getTotalCount(),
                response.getQueryResult().getSearchPosition());
    }

    private PromotionCodeReq promotion(GoodsSnapshot goods, String userId, String promotionId) {
        PromotionCodeReq req = new PromotionCodeReq();
        String material = goods.getGoodsUrl();
        if (material == null || material.isBlank()) {
            material = "https://item.jd.com/" + goods.getPlatformGoodsId() + ".html";
        }
        req.setMaterialId(material);
        req.setSceneId(1);
        req.setPid(UnionMaps.promotion(promotionId));
        String buyer = UnionMaps.userId(userId);
        if (buyer != null) {
            req.setSubUnionId(buyer);
        }
        return req;
    }

    private static void applySort(GoodsReq req, String sort) {
        if (sort == null) {
            return;
        }
        switch (sort) {
            case "sales" -> {
                req.setSortName("inOrderCount30Days");
                req.setSort("desc");
            }
            case "price" -> {
                req.setSortName("price");
                req.setSort("asc");
            }
            case "commission" -> {
                req.setSortName("commissionShare");
                req.setSort("desc");
            }
            case "coupon" -> req.setIsCoupon(1);
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

    private record QueryBatch(GoodsResp[] rows, long total, String nextId) {
    }

    private void pullAxis(DefaultJdClient http, int type, UnionMaps.Window window, Map<String, IncomingOrderDto> merged)
            throws Exception {
        boolean finished = false;
        for (int page = 1; page <= UnionMaps.MAX_ORDER_PAGES; page++) {
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
                var result = response == null ? null : response.getQueryResult();
                throw new PlatformCallException(response == null
                        ? "京东订单响应为空"
                        : PlatformCallException.explain(
                                response.getCode(),
                                result == null ? null : Integer.toString(result.getCode()),
                                result == null ? null : result.getMessage()));
            }
            OrderRowResp[] rows = response.getQueryResult().getData();
            if (rows != null) {
                for (OrderRowResp row : rows) {
                    IncomingOrderDto order = order(row);
                    if (order != null) {
                        merged.put(order.platformOrderId(), order);
                    }
                }
            }
            if (rows == null || rows.length < 50 || !Boolean.TRUE.equals(response.getQueryResult().getHasMore())) {
                finished = true;
                break;
            }
        }
        if (!finished) {
            throw new PlatformCallException("本段订单超过 40 页，水位不前进");
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

    private static GoodsSnapshot materialSnapshot(MaterialGoodsResp row) {
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

    private static IncomingOrderDto order(OrderRowResp row) {
        if (row.getOrderId() == null) {
            return null;
        }
        OrderStatus status = UnionMaps.jdStatus(row.getValidCode());
        String image = row.getGoodsInfo() == null ? null : UnionMaps.http(row.getGoodsInfo().getImageUrl());
        String goodsId = row.getItemId();
        if (goodsId == null || goodsId.isBlank()) {
            goodsId = row.getSkuId() == null ? null : row.getSkuId().toString();
        }
        return new IncomingOrderDto(
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
                status == OrderStatus.COMPLETED ? UnionMaps.parseTime(row.getFinishTime()) : null,
                null,
                UnionMaps.parseTime(row.getFinishTime()),
                null);
    }
}
