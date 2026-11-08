package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.app.util.SdkSessions;

import com.omino.shopai.app.util.Upstream;

import com.omino.shopai.app.util.SdkLog;

import com.omino.shopai.app.util.CountMaps;

import com.omino.shopai.app.util.UnionMaps;

import com.omino.shopai.app.dto.GoodsHitPage;
import com.omino.shopai.app.dto.GoodsSearchRequestDto;
import com.omino.shopai.app.dto.PromotionLinkDto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.pdd.pop.sdk.http.api.pop.request.PddDdkGoodsRecommendGetRequest;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkGoodsSearchRequest;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkMemberAuthorityQueryRequest;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkOrderListIncrementGetRequest;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkRpPromUrlGenerateRequest;
import com.pdd.pop.sdk.http.api.pop.response.PddDdkGoodsDetailResponse;
import com.pdd.pop.sdk.http.api.pop.response.PddDdkGoodsPromotionUrlGenerateResponse;
import com.pdd.pop.sdk.http.api.pop.response.PddDdkGoodsRecommendGetResponse;
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
 * 拼多多联盟。商品搜索、详情、推广链接、增量订单，以及备案查询和授权页。相似和猜你喜欢走商品推荐，不生成口令。
 */
@Component
public class PddGoodsImpl implements IPlatformGoods {

    private final String clientId;
    private final String clientSecret;
    /** 广告位ID，不从环境变量读。 */
    private static final String PID = "9845485_144459674";

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
            bind(request, promotionId);
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
        return searchPage(GoodsSearchRequestDto.firstPage(platform().code(), keyword), userId, promotionId).list();
    }

    @Override
    public GoodsHitPage searchPage(GoodsSearchRequestDto query, String userId, String promotionId) {
        Upstream.call(clientId, clientSecret);
        if (query == null || query.keyword() == null || query.keyword().isBlank()) {
            return GoodsHitPage.empty();
        }
        try {
            PddDdkGoodsSearchRequest request = new PddDdkGoodsSearchRequest();
            request.setKeyword(query.keyword());
            request.setPage(Math.max(query.page() == null ? 1 : query.page(), 1));
            request.setPageSize(clamp(query.size() == null ? 20 : query.size()));
            applySort(request, query.sort());
            applyFilters(request, query);
            bind(request, promotionId);
            PddDdkGoodsSearchResponse response = SdkLog.exchange(
                    "pdd", "goods.search", request, () -> client().syncInvoke(request));
            if (response == null || response.getErrorResponse() != null || response.getGoodsSearchResponse() == null) {
                throw new PlatformCallException();
            }
            List<GoodsSnapshot> found = new ArrayList<>();
            var rows = response.getGoodsSearchResponse().getGoodsList();
            if (rows != null) {
                for (PddDdkGoodsSearchResponse.GoodsSearchResponseGoodsListItem row : rows) {
                    GoodsSnapshot snapshot = searchSnapshot(row);
                    if (snapshot != null) {
                        found.add(snapshot);
                    }
                }
            }
            Integer total = response.getGoodsSearchResponse().getTotalCount();
            return new GoodsHitPage(
                    found,
                    total == null ? found.size() : total,
                    response.getGoodsSearchResponse().getListId());
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
        return recommend(platformGoodsId, page, size, userId, promotionId);
    }

    @Override
    public boolean offersGuess() {
        return true;
    }

    @Override
    public GoodsHitPage guess(int page, int size, String userId, String promotionId) {
        return recommend(null, page, size, userId, promotionId);
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
            bind(request, promotionId);
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

    /**
     * 拼多多只有更新时间。下单段不请求。页拉完才返回，页还满则本段失败。
     *
     * @param cursor 当前游标
     * @param created 下单时间这一段，这里不使用
     * @param updated 更新时间这一段
     * @return 这一段里的订单
     */
    @Override
    public List<IncomingOrderDto> pullOrders(OrderSyncCursor cursor, UnionMaps.Window created, UnionMaps.Window updated) {
        Upstream.call(clientId, clientSecret);
        if (updated == null) {
            return List.of();
        }
        try {
            PopHttpClient http = client();
            List<IncomingOrderDto> found = new ArrayList<>();
            boolean finished = false;
            for (int page = 1; page <= UnionMaps.MAX_ORDER_PAGES; page++) {
                PddDdkOrderListIncrementGetRequest request = new PddDdkOrderListIncrementGetRequest();
                request.setStartUpdateTime(updated.start().toEpochSecond());
                request.setEndUpdateTime(updated.end().toEpochSecond());
                request.setPage(page);
                request.setPageSize(50);
                request.setReturnCount(false);
                PddDdkOrderListIncrementGetResponse response = SdkLog.exchange(
                        "pdd", "order.increment", request, () -> http.syncInvoke(request));
                if (response == null || response.getErrorResponse() != null) {
                    var err = response == null ? null : response.getErrorResponse();
                    throw new PlatformCallException(response == null
                            ? "拼多多订单响应为空"
                            : PlatformCallException.explain(
                                    err.getErrorCode() == null ? null : err.getErrorCode().toString(),
                                    err.getErrorMsg(),
                                    err.getSubMsg()));
                }
                var rows = response.getOrderListGetResponse() == null
                        ? null
                        : response.getOrderListGetResponse().getOrderList();
                if (rows != null) {
                    for (var row : rows) {
                        IncomingOrderDto order = order(row);
                        if (order != null) {
                            found.add(order);
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
            return found;
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException(ex);
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
    public static void bind(PddDdkGoodsSearchRequest request, String promotionId) {
        request.setPid(PID);
        request.setCustomParameters(custom(promotionId));
    }

    /** 详情与搜品使用同一对备案参数。 */
    public static void bind(PddDdkGoodsDetailRequest request, String promotionId) {
        request.setPid(PID);
        request.setCustomParameters(custom(promotionId));
    }

    /** 推荐和搜品使用同一对备案参数。 */
    public static void bind(PddDdkGoodsRecommendGetRequest request, String promotionId) {
        request.setPid(PID);
        request.setCustomParameters(custom(promotionId));
    }

    /** 转链请求的推广位方法名是 setPId。 */
    public static void bind(PddDdkGoodsPromotionUrlGenerateRequest request, String promotionId) {
        request.setPId(PID);
        request.setCustomParameters(custom(promotionId));
    }

    private static String custom(String promotionId) {
        return "{\"uid\":\"" + promotionId + "\"}";
    }

    /**
     * 从订单上的备案参数取出推广身份。官方字段是 JSON 字符串，键为 uid。
     *
     * @param customParameters 订单上的 custom_parameters
     * @return uid；没有或不是 JSON 则为空
     */
    private static String relationId(String customParameters) {
        if (customParameters == null || customParameters.isBlank()) {
            return null;
        }
        try {
            String uid = new ObjectMapper().readTree(customParameters).path("uid").asText("");
            return uid.isBlank() ? null : uid;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private GoodsHitPage recommend(String platformGoodsId, int page, int size, String userId, String promotionId) {
        Upstream.call(clientId, clientSecret);
        try {
            int pageSize = clamp(size);
            PddDdkGoodsRecommendGetRequest request = new PddDdkGoodsRecommendGetRequest();
            request.setOffset(Math.max(page - 1, 0) * pageSize);
            request.setLimit(pageSize);
            if (platformGoodsId != null && !platformGoodsId.isBlank()) {
                request.setGoodsSignList(List.of(platformGoodsId));
            }
            bind(request, promotionId);
            PddDdkGoodsRecommendGetResponse response = SdkLog.exchange(
                    "pdd", "goods.recommend", request, () -> client().syncInvoke(request));
            if (response == null || response.getErrorResponse() != null || response.getGoodsBasicDetailResponse() == null) {
                throw new PlatformCallException();
            }
            List<GoodsSnapshot> found = new ArrayList<>();
            var rows = response.getGoodsBasicDetailResponse().getList();
            if (rows != null) {
                for (PddDdkGoodsRecommendGetResponse.GoodsBasicDetailResponseListItem row : rows) {
                    GoodsSnapshot snapshot = recommendSnapshot(row);
                    if (snapshot != null) {
                        found.add(snapshot);
                    }
                }
            }
            Integer total = response.getGoodsBasicDetailResponse().getTotal();
            return new GoodsHitPage(found, total == null ? found.size() : total);
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    /**
     * 把品牌、有券、价格和翻页游标写到拼多多能认的字段上。
     *
     * @param request 搜索请求
     * @param query 已经校验过的搜索条件
     */
    private static void applyFilters(PddDdkGoodsSearchRequest request, GoodsSearchRequestDto query) {
        if (Boolean.TRUE.equals(query.brand())) {
            request.setIsBrandGoods(true);
        }
        if (Boolean.TRUE.equals(query.coupon())) {
            request.setWithCoupon(true);
        }
        if (query.nextId() != null && !query.nextId().isBlank()) {
            request.setListId(query.nextId());
        }
        if (query.priceMin() == null && query.priceMax() == null) {
            return;
        }
        // rangeId 1 是券后价，单位是分。没传的那一端不限制。
        PddDdkGoodsSearchRequest.RangeListItem item = new PddDdkGoodsSearchRequest.RangeListItem();
        item.setRangeId(1);
        if (query.priceMin() != null) {
            item.setRangeFrom(GoodsSearchRequestDto.fen(query.priceMin()));
        }
        if (query.priceMax() != null) {
            item.setRangeTo(GoodsSearchRequestDto.fen(query.priceMax()));
        }
        request.setRangeList(List.of(item));
    }

    private static void applySort(PddDdkGoodsSearchRequest request, String sort) {
        if (sort == null) {
            return;
        }
        switch (sort) {
            case "sales" -> request.setSortType(6);
            case "price" -> request.setSortType(3);
            case "commission" -> request.setSortType(2);
            case "coupon" -> request.setWithCoupon(true);
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

    private static GoodsSnapshot recommendSnapshot(PddDdkGoodsRecommendGetResponse.GoodsBasicDetailResponseListItem row) {
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
                null,
                null,
                goodsId,
                row.getGoodsName(),
                UnionMaps.http(row.getGoodsThumbnailUrl()),
                UnionMaps.fen(row.getOrderAmount()),
                UnionMaps.fen(row.getPromotionAmount()),
                status,
                UnionMaps.fromEpoch(row.getOrderCreateTime()),
                status == OrderStatus.COMPLETED ? UnionMaps.fromEpoch(row.getOrderPayTime()) : null,
                UnionMaps.fromEpoch(row.getOrderPayTime()),
                UnionMaps.fromEpoch(row.getOrderReceiveTime()),
                null,
                relationId(row.getCustomParameters()));
    }
}
