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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taobao.api.AutoRetryTaobaoClient;
import com.taobao.api.request.TbkDgGeneralLinkConvertRequest;
import com.taobao.api.request.TbkDgMaterialOptionalUpgradeRequest;
import com.taobao.api.request.TbkItemInfoUpgradeGetRequest;
import com.taobao.api.request.TbkOrderDetailsGetRequest;
import com.taobao.api.request.TbkScPublisherInfoSaveRequest;
import com.taobao.api.request.TopAuthTokenCreateRequest;
import com.taobao.api.response.TbkDgGeneralLinkConvertResponse;
import com.taobao.api.response.TbkDgMaterialOptionalUpgradeResponse;
import com.taobao.api.response.TbkItemInfoUpgradeGetResponse;
import com.taobao.api.response.TbkOrderDetailsGetResponse;
import com.taobao.api.response.TbkScPublisherInfoSaveResponse;
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
 * 淘宝客。只用物料搜索、商品详情、万能转链、订单明细，以及授权换票和渠道备案。
 */
@Component
public class TaobaoGoodsImpl implements IPlatformGoods {

    private static final String GATEWAY = "https://eco.taobao.com/router/rest";
    private static final long MATERIAL_ID = 6707L;
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
        Upstream.call(appKey, appSecret);
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        try {
            TbkDgMaterialOptionalUpgradeRequest request = new TbkDgMaterialOptionalUpgradeRequest();
            request.setQ(keyword);
            request.setAdzoneId(ADZONE_ID);
            request.setRelationId(UnionMaps.promotion(promotionId));
            request.setMaterialId(MATERIAL_ID);
            request.setPageNo(1L);
            request.setPageSize(20L);
            TbkDgMaterialOptionalUpgradeResponse response = SdkLog.exchange(
                    "taobao", "material.optional", request, () -> client().execute(request));
            if (response == null || !response.isSuccess()) {
                throw new PlatformCallException();
            }
            List<TbkDgMaterialOptionalUpgradeResponse.MapData> rows = response.getResultList();
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<GoodsSnapshot> found = new ArrayList<>();
            for (TbkDgMaterialOptionalUpgradeResponse.MapData row : rows) {
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
        Upstream.call(appKey, appSecret);
        String itemId = goods == null ? null : goods.getPlatformGoodsId();
        if (itemId == null || itemId.isBlank()) {
            throw new PlatformCallException();
        }
        try {
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
            TbkDgGeneralLinkConvertResponse.ItemLinkInfoDTO info = item.getLinkInfoDto();
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

    @Override
    public List<IncomingOrderDto> pullOrders(OrderSyncCursor cursor) {
        Upstream.call(appKey, appSecret);
        try {
            AutoRetryTaobaoClient http = client();
            Map<String, IncomingOrderDto> merged = new LinkedHashMap<>();
            UnionMaps.Window created = UnionMaps.window(cursor == null ? null : cursor.getLastSyncCreateTime());
            UnionMaps.Window updated = UnionMaps.window(cursor == null ? null : cursor.getLastSyncUpdateTime());
            pullAxis(http, 1L, created, merged);
            pullAxis(http, 4L, updated, merged);
            return List.copyOf(merged.values());
        } catch (PlatformCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformCallException();
        }
    }

    private void pullAxis(AutoRetryTaobaoClient http, long queryType, UnionMaps.Window window, Map<String, IncomingOrderDto> merged)
            throws Exception {
        for (long page = 1; page <= 3; page++) {
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
                throw new PlatformCallException();
            }
            List<TbkOrderDetailsGetResponse.PublisherOrderDto> rows = response.getData() == null
                    ? List.of()
                    : response.getData().getResults();
            if (rows == null || rows.isEmpty()) {
                return;
            }
            for (TbkOrderDetailsGetResponse.PublisherOrderDto row : rows) {
                IncomingOrderDto order = order(row);
                if (order != null) {
                    merged.put(order.platformOrderId(), order);
                }
            }
            if (rows.size() < 50) {
                return;
            }
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
        return new IncomingOrderDto(
                id,
                null,
                null,
                row.getItemId(),
                row.getItemTitle(),
                UnionMaps.http(row.getItemImg()),
                UnionMaps.money(row.getPayPrice()),
                UnionMaps.money(row.getPubShareFee()),
                status,
                orderedAt,
                shippedAt);
    }
}
