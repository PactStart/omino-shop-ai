package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.app.util.SdkSessions;

import com.omino.shopai.app.util.Upstream;

import com.omino.shopai.app.util.SdkLog;

import com.omino.shopai.app.util.CountMaps;

import com.omino.shopai.app.util.UnionMaps;

import com.omino.shopai.app.dto.PromotionLinkDto;

import com.omino.shopai.exception.PlatformCallException;

import com.omino.shopai.dto.order.IncomingOrderDto;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.enums.OrderStatus;
import com.omino.shopai.enums.Platform;
import com.pdd.pop.sdk.http.HttpClientConfig;
import com.pdd.pop.sdk.http.PopHttpClient;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkGoodsDetailRequest;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkGoodsPromotionUrlGenerateRequest;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkGoodsSearchRequest;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkMemberAuthorityQueryRequest;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkOrderListIncrementGetRequest;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkRpPromUrlGenerateRequest;
import com.pdd.pop.sdk.http.api.pop.response.PddDdkGoodsDetailResponse;
import com.pdd.pop.sdk.http.api.pop.response.PddDdkGoodsPromotionUrlGenerateResponse;
import com.pdd.pop.sdk.http.api.pop.response.PddDdkGoodsSearchResponse;
import com.pdd.pop.sdk.http.api.pop.response.PddDdkMemberAuthorityQueryResponse;
import com.pdd.pop.sdk.http.api.pop.response.PddDdkOrderListIncrementGetResponse;
import com.pdd.pop.sdk.http.api.pop.response.PddDdkRpPromUrlGenerateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 拼多多联盟。只用商品搜索、详情、推广链接、增量订单，以及备案查询和授权页。
 */
@Component
public class PddGoodsImpl implements IPlatformGoods {

    private final String clientId;
    private final String clientSecret;

    public PddGoodsImpl(
            @Value("${UNION_PDD_CLIENT_ID:}") String clientId,
            @Value("${UNION_PDD_CLIENT_SECRET:}") String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * 这个实现负责拼多多。
     *
     * @return 拼多多
     */
    @Override
    public Platform platform() {
        return Platform.PDD;
    }

    @Override
    public GoodsSnapshot detail(String platformGoodsId, String userId, String promotionId) {
        Upstream.call(clientId, clientSecret);
        if (platformGoodsId == null || platformGoodsId.isBlank()) {
            throw new PlatformCallException();
        }
        try {
            PddDdkGoodsDetailRequest request = new PddDdkGoodsDetailRequest();
            request.setGoodsSign(platformGoodsId);
            bind(request, promotionId, userId);
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
    public List<GoodsSnapshot> search(String keyword, String userId, String promotionId) {
        Upstream.call(clientId, clientSecret);
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        try {
            PddDdkGoodsSearchRequest request = new PddDdkGoodsSearchRequest();
            request.setKeyword(keyword);
            request.setPage(1);
            request.setPageSize(20);
            bind(request, promotionId, userId);
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
    public PromotionLinkDto convert(GoodsSnapshot goods, boolean openInApp, String userId, String promotionId) {
        Upstream.call(clientId, clientSecret);
        String sign = goods == null ? null : goods.getPlatformGoodsId();
        if (sign == null || sign.isBlank()) {
            throw new PlatformCallException();
        }
        try {
            PddDdkGoodsPromotionUrlGenerateRequest request = new PddDdkGoodsPromotionUrlGenerateRequest();
            request.setGoodsSignList(List.of(sign));
            request.setGenerateSchemaUrl(openInApp);
            bind(request, promotionId, userId);
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
            return new PromotionLinkDto(url, deeplink, null, url);
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    @Override
    public List<IncomingOrderDto> pullOrders(OrderSyncCursor cursor) {
        Upstream.call(clientId, clientSecret);
        try {
            PopHttpClient http = client();
            UnionMaps.Window window = UnionMaps.window(cursor == null ? null : cursor.getLastSyncUpdateTime());
            List<IncomingOrderDto> found = new ArrayList<>();
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
                    IncomingOrderDto order = order(row);
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

    /**
     * 问这个用户是否已经在该推广身份下备案。bind 为 1 才算已授权。
     *
     * @param userId 当前用户
     * @param pid 京东那条授权上的推广身份
     * @return 已备案时为真
     */
    public boolean bound(String userId, String pid) {
        Upstream.call(clientId, clientSecret);
        try {
            PddDdkMemberAuthorityQueryRequest request = new PddDdkMemberAuthorityQueryRequest();
            request.setPid(UnionMaps.promotion(pid));
            request.setCustomParameters(custom(pid));
            PddDdkMemberAuthorityQueryResponse response = SdkLog.exchange(
                    "pdd", "member.authority", request, () -> client().syncInvoke(request));
            if (response == null || response.getErrorResponse() != null || response.getAuthorityQueryResponse() == null) {
                throw new PlatformCallException();
            }
            return Integer.valueOf(1).equals(response.getAuthorityQueryResponse().getBind());
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    /**
     * 生成一条备案授权页。渠道类型 10 是授权页，不是商品推广。
     *
     * @param userId 当前用户
     * @param pid 京东那条授权上的推广身份
     * @return 给客户端打开的一条链接
     */
    public String authUrl(String userId, String pid) {
        Upstream.call(clientId, clientSecret);
        try {
            PddDdkRpPromUrlGenerateRequest request = new PddDdkRpPromUrlGenerateRequest();
            request.setChannelType(10);
            request.setCustomParameters(custom(userId));
            request.setGenerateSchemaUrl(true);
            request.setGenerateShortUrl(true);
            request.setPIdList(List.of(UnionMaps.promotion(pid)));
            PddDdkRpPromUrlGenerateResponse response = SdkLog.exchange(
                    "pdd", "rp.prom.url", request, () -> client().syncInvoke(request));
            if (response == null || response.getErrorResponse() != null || response.getRpPromotionUrlGenerateResponse() == null) {
                throw new PlatformCallException();
            }
            var list = response.getRpPromotionUrlGenerateResponse().getUrlList();
            if (list == null || list.isEmpty()) {
                throw new PlatformCallException();
            }
            PddDdkRpPromUrlGenerateResponse.RpPromotionUrlGenerateResponseUrlListItem item = list.get(0);
            String url = UnionMaps.first(item.getMobileUrl(), item.getUrl(), item.getShortUrl(), item.getMobileShortUrl(), item.getSchemaUrl());
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

    /** 拼多多搜品、详情、转链都要带上已备案的 pid 和用户号。 */
    public static void bind(PddDdkGoodsSearchRequest request, String pid, String userId) {
        request.setPid(UnionMaps.promotion(pid));
        request.setCustomParameters(custom(userId));
    }

    /** 详情与搜品使用同一对备案参数。 */
    public static void bind(PddDdkGoodsDetailRequest request, String pid, String userId) {
        request.setPid(UnionMaps.promotion(pid));
        request.setCustomParameters(custom(userId));
    }

    /** 转链请求的推广位方法名是 setPId。 */
    public static void bind(PddDdkGoodsPromotionUrlGenerateRequest request, String pid, String userId) {
        request.setPId(UnionMaps.promotion(pid));
        request.setCustomParameters(custom(userId));
    }

    private static String custom(String userId) {
        String buyer = UnionMaps.userId(userId);
        if (buyer == null) {
            throw new PlatformCallException();
        }
        return "{\"uid\":\"" + buyer + "\"}";
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

    private static IncomingOrderDto order(PddDdkOrderListIncrementGetResponse.OrderListGetResponseOrderListItem row) {
        String id = row.getOrderSn();
        if (id == null || id.isBlank()) {
            id = row.getOrderId();
        }
        if (id == null || id.isBlank()) {
            return null;
        }
        OrderStatus status = UnionMaps.pddStatus(row.getOrderStatus());
        String goodsId = row.getGoodsSign();
        if (goodsId == null || goodsId.isBlank()) {
            goodsId = row.getGoodsId() == null ? null : row.getGoodsId().toString();
        }
        return new IncomingOrderDto(
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
                status == OrderStatus.COMPLETED ? UnionMaps.fromEpoch(row.getOrderPayTime()) : null);
    }
}
