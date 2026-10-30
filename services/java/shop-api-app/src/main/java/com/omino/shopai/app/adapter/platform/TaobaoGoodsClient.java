package com.omino.shopai.app.adapter.platform;

import com.omino.shopai.dto.order.IncomingOrder;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.enums.Platform;
import com.taobao.api.AutoRetryTaobaoClient;
import com.taobao.api.request.TbkDgGeneralLinkConvertRequest;
import com.taobao.api.request.TbkDgMaterialOptionalUpgradeRequest;
import com.taobao.api.request.TbkItemInfoUpgradeGetRequest;
import com.taobao.api.request.TbkOrderDetailsGetRequest;
import com.taobao.api.response.TbkDgGeneralLinkConvertResponse;
import com.taobao.api.response.TbkDgMaterialOptionalUpgradeResponse;
import com.taobao.api.response.TbkItemInfoUpgradeGetResponse;
import com.taobao.api.response.TbkOrderDetailsGetResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 淘宝客。只用物料搜索、商品详情、万能转链和订单明细。
 */
@Component
public class TaobaoGoodsClient implements PlatformGoodsClient {

    private static final String GATEWAY = "https://eco.taobao.com/router/rest";
    private static final long MATERIAL_ID = 6707L;
    /** 搜品和转链的广告位。与参考客户端的默认值相同，不从环境变量读。 */
    private static final long ADZONE_ID = 110216900017L;

    private final String appKey;
    private final String appSecret;

    public TaobaoGoodsClient(
            @Value("${UNION_TAOBAO_APP_KEY:}") String appKey,
            @Value("${UNION_TAOBAO_APP_SECRET:}") String appSecret) {
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    @Override
    public String platform() {
        return "taobao";
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
    public PromotionLink convert(GoodsSnapshot goods, boolean openInApp, String userId, String promotionId) {
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
            return new PromotionLink(url, null, null, url);
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
            AutoRetryTaobaoClient http = client();
            Map<String, IncomingOrder> merged = new LinkedHashMap<>();
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

    private void pullAxis(AutoRetryTaobaoClient http, long queryType, UnionMaps.Window window, Map<String, IncomingOrder> merged)
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

    private static IncomingOrder order(TbkOrderDetailsGetResponse.PublisherOrderDto row) {
        String id = row.getTradeId();
        if (id == null || id.isBlank()) {
            return null;
        }
        String status = UnionMaps.taobaoStatus(row.getTkStatus());
        OffsetDateTime orderedAt = UnionMaps.parseTime(row.getTkCreateTime());
        OffsetDateTime shippedAt = "completed".equals(status) ? UnionMaps.parseTime(row.getTkEarningTime()) : null;
        return new IncomingOrder(
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
