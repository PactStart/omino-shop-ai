package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.dto.order.IncomingOrder;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.enums.Platform;
import com.pdd.pop.sdk.http.HttpClientConfig;
import com.pdd.pop.sdk.http.PopHttpClient;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkGoodsDetailRequest;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkGoodsPromotionUrlGenerateRequest;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkGoodsSearchRequest;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkOrderListIncrementGetRequest;
import com.pdd.pop.sdk.http.api.pop.response.PddDdkGoodsDetailResponse;
import com.pdd.pop.sdk.http.api.pop.response.PddDdkGoodsPromotionUrlGenerateResponse;
import com.pdd.pop.sdk.http.api.pop.response.PddDdkGoodsSearchResponse;
import com.pdd.pop.sdk.http.api.pop.response.PddDdkOrderListIncrementGetResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 拼多多联盟。只用商品搜索、详情、推广链接和增量订单。
 */
@Component
public class PddGoodsClient implements PlatformGoodsClient {

    private final String clientId;
    private final String clientSecret;

    public PddGoodsClient(
            @Value("${UNION_PDD_CLIENT_ID:}") String clientId,
            @Value("${UNION_PDD_CLIENT_SECRET:}") String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public String platform() {
        return "pdd";
    }

    @Override
    public GoodsSnapshot detail(String platformGoodsId) {
        Upstream.call(clientId, clientSecret);
        if (platformGoodsId == null || platformGoodsId.isBlank()) {
            throw new PlatformCallException();
        }
        try {
            PddDdkGoodsDetailRequest request = new PddDdkGoodsDetailRequest();
            request.setGoodsSign(platformGoodsId);
            PddDdkGoodsDetailResponse response = SdkLog.exchange(
                    "pdd", "goods.detail", request, () -> client().syncInvoke(request));
            if (response == null || response.getErrorResponse() != null) {
                throw new PlatformCallException();
            }
            if (response.getGoodsDetailResponse() == null || response.getGoodsDetailResponse().getGoodsDetails() == null
                    || response.getGoodsDetailResponse().getGoodsDetails().isEmpty()) {
                return null;
            }
            return detailSnapshot(response.getGoodsDetailResponse().getGoodsDetails().get(0));
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    @Override
    public List<GoodsSnapshot> search(String keyword) {
        Upstream.call(clientId, clientSecret);
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        try {
            PddDdkGoodsSearchRequest request = new PddDdkGoodsSearchRequest();
            request.setKeyword(keyword);
            request.setPage(1);
            request.setPageSize(20);
            PddDdkGoodsSearchResponse response = SdkLog.exchange(
                    "pdd", "goods.search", request, () -> client().syncInvoke(request));
            if (response == null || response.getErrorResponse() != null) {
                throw new PlatformCallException();
            }
            if (response.getGoodsSearchResponse() == null || response.getGoodsSearchResponse().getGoodsList() == null) {
                return List.of();
            }
            List<GoodsSnapshot> found = new ArrayList<>();
            for (PddDdkGoodsSearchResponse.GoodsSearchResponseGoodsListItem row : response.getGoodsSearchResponse().getGoodsList()) {
                GoodsSnapshot snapshot = searchSnapshot(row);
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
        Upstream.call(clientId, clientSecret);
        String sign = goods == null ? null : goods.getPlatformGoodsId();
        if (sign == null || sign.isBlank()) {
            throw new PlatformCallException();
        }
        try {
            PddDdkGoodsPromotionUrlGenerateRequest request = new PddDdkGoodsPromotionUrlGenerateRequest();
            request.setGoodsSignList(List.of(sign));
            request.setGenerateSchemaUrl(openInApp);
            String buyer = UnionMaps.userId(userId);
            if (buyer != null) {
                request.setCustomParameters("{\"uid\":\"" + buyer + "\"}");
            }
            PddDdkGoodsPromotionUrlGenerateResponse response = SdkLog.exchange(
                    "pdd", "promotion.url", request, () -> client().syncInvoke(request));
            if (response == null || response.getErrorResponse() != null || response.getGoodsPromotionUrlGenerateResponse() == null) {
                throw new PlatformCallException();
            }
            var list = response.getGoodsPromotionUrlGenerateResponse().getGoodsPromotionUrlList();
            if (list == null || list.isEmpty()) {
                throw new PlatformCallException();
            }
            var item = list.get(0);
            String url = UnionMaps.first(item.getMobileUrl(), item.getUrl(), item.getShortUrl());
            if (url == null) {
                throw new PlatformCallException();
            }
            String deeplink = openInApp ? UnionMaps.http(item.getSchemaUrl()) : null;
            return new PromotionLink(url, deeplink, null, url);
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    @Override
    public List<IncomingOrder> pullOrders(OrderSyncCursor cursor) {
        Upstream.call(clientId, clientSecret);
        try {
            PopHttpClient http = client();
            UnionMaps.Window window = UnionMaps.window(cursor == null ? null : cursor.getLastSyncUpdateTime());
            List<IncomingOrder> found = new ArrayList<>();
            for (int page = 1; page <= 3; page++) {
                PddDdkOrderListIncrementGetRequest request = new PddDdkOrderListIncrementGetRequest();
                request.setStartUpdateTime(window.start().toEpochSecond());
                request.setEndUpdateTime(window.end().toEpochSecond());
                request.setPage(page);
                request.setPageSize(50);
                request.setReturnCount(false);
                PddDdkOrderListIncrementGetResponse response = SdkLog.exchange(
                        "pdd", "order.increment", request, () -> http.syncInvoke(request));
                if (response == null || response.getErrorResponse() != null) {
                    throw new PlatformCallException();
                }
                var rows = response.getOrderListGetResponse() == null
                        ? null
                        : response.getOrderListGetResponse().getOrderList();
                if (rows == null || rows.isEmpty()) {
                    break;
                }
                for (var row : rows) {
                    IncomingOrder order = order(row);
                    if (order != null) {
                        found.add(order);
                    }
                }
                if (rows.size() < 50) {
                    break;
                }
            }
            return found;
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    private PopHttpClient client() {
        HttpClientConfig config = new HttpClientConfig();
        config.setConnectionTimeoutMillis(UnionMaps.TIMEOUT_MS);
        config.setSocketTimeoutMillis(UnionMaps.TIMEOUT_MS);
        config.setConnectionRequestTimeout(UnionMaps.TIMEOUT_MS);
        SdkSessions.markOpened();
        return new PopHttpClient(clientId, clientSecret, config);
    }

    private static GoodsSnapshot searchSnapshot(PddDdkGoodsSearchResponse.GoodsSearchResponseGoodsListItem row) {
        String id = row.getGoodsSign();
        if (id == null || id.isBlank()) {
            id = row.getGoodsId() == null ? null : row.getGoodsId().toString();
        }
        if (id == null) {
            return null;
        }
        GoodsSnapshot snapshot = base(id);
        snapshot.setTitle(row.getGoodsName());
        snapshot.setImageUrl(UnionMaps.http(row.getGoodsImageUrl()));
        snapshot.setShopName(row.getMallName());
        snapshot.setSalesCount(CountMaps.sales(row.getSalesTip()));
        snapshot.setPrice(UnionMaps.fen(row.getMinNormalPrice()));
        snapshot.setCouponPrice(UnionMaps.fen(row.getMinGroupPrice()));
        return snapshot;
    }

    private static GoodsSnapshot detailSnapshot(PddDdkGoodsDetailResponse.GoodsDetailResponseGoodsDetailsItem row) {
        String id = row.getGoodsSign();
        if (id == null || id.isBlank()) {
            id = row.getGoodsId() == null ? null : row.getGoodsId().toString();
        }
        if (id == null) {
            return null;
        }
        GoodsSnapshot snapshot = base(id);
        snapshot.setTitle(row.getGoodsName());
        snapshot.setImageUrl(UnionMaps.http(row.getGoodsImageUrl()));
        snapshot.setShopName(row.getMallName());
        snapshot.setSalesCount(CountMaps.sales(row.getSalesTip()));
        snapshot.setPrice(UnionMaps.fen(row.getMinNormalPrice()));
        snapshot.setCouponPrice(UnionMaps.fen(row.getMinGroupPrice()));
        return snapshot;
    }

    private static GoodsSnapshot base(String id) {
        GoodsSnapshot snapshot = new GoodsSnapshot();
        snapshot.setPlatform(Platform.PDD);
        snapshot.setPlatformGoodsId(id);
        snapshot.setUpdatedAt(OffsetDateTime.now(UnionMaps.SHANGHAI));
        return snapshot;
    }

    private static IncomingOrder order(PddDdkOrderListIncrementGetResponse.OrderListGetResponseOrderListItem row) {
        String id = row.getOrderSn();
        if (id == null || id.isBlank()) {
            id = row.getOrderId();
        }
        if (id == null || id.isBlank()) {
            return null;
        }
        String status = UnionMaps.pddStatus(row.getOrderStatus());
        String goodsId = row.getGoodsSign();
        if (goodsId == null || goodsId.isBlank()) {
            goodsId = row.getGoodsId() == null ? null : row.getGoodsId().toString();
        }
        return new IncomingOrder(
                id,
                UnionMaps.userId(row.getCustomParameters()),
                null,
                goodsId,
                row.getGoodsName(),
                UnionMaps.http(row.getGoodsThumbnailUrl()),
                UnionMaps.fen(row.getOrderAmount()),
                UnionMaps.fen(row.getPromotionAmount()),
                status,
                UnionMaps.fromEpoch(row.getOrderCreateTime()),
                "completed".equals(status) ? UnionMaps.fromEpoch(row.getOrderPayTime()) : null);
    }
}
