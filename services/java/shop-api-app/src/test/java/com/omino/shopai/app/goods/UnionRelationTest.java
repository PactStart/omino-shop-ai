package com.omino.shopai.app.goods;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.adapter.platform.PddGoodsClient;
import com.omino.shopai.app.adapter.platform.PlatformCallException;
import com.omino.shopai.app.adapter.platform.SdkSessions;
import com.omino.shopai.app.adapter.platform.VipGoodsClient;
import com.omino.shopai.app.service.goods.CachingUnionRelation;
import com.omino.shopai.app.service.goods.GoodsQueryService;
import com.omino.shopai.app.service.goods.RelationLookup;
import com.omino.shopai.app.service.goods.TextCache;
import com.omino.shopai.app.service.goods.UnionIds;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkGoodsPromotionUrlGenerateRequest;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkGoodsSearchRequest;
import com.vip.adp.api.open.service.GetByGoodsIdsRequest;
import com.vip.adp.api.open.service.QueryGoodsRequest;
import com.vip.adp.api.open.service.UrlGenByGoodsIdRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnionRelationTest {

    private static final String USER = "01HJKMNPQRSTVWXYZ00000000A";

    @Test
    void pddPidComesFromTheJdRelationAndCustomParametersCarryTheUser() throws Exception {
        PddDdkGoodsSearchRequest search = new PddDdkGoodsSearchRequest();
        PddGoodsClient.bind(search, "501", USER);
        PddDdkGoodsPromotionUrlGenerateRequest convert = new PddDdkGoodsPromotionUrlGenerateRequest();
        PddGoodsClient.bind(convert, "501", USER);
        String searchJson = fields(search);
        String convertJson = fields(convert);
        assertTrue(searchJson.contains("501"));
        assertTrue(searchJson.contains("uid"));
        assertTrue(searchJson.contains(USER));
        assertTrue(convertJson.contains("501"));
        assertTrue(convertJson.contains("uid"));
        assertTrue(convertJson.contains(USER));
        assertEquals("jd", UnionIds.source("pdd"));
        assertEquals("taobao", UnionIds.source("taobao"));
    }

    @Test
    void vipRelationGoesToOpenIdAndChanTagStaysDefault() {
        QueryGoodsRequest search = new QueryGoodsRequest();
        GetByGoodsIdsRequest detail = new GetByGoodsIdsRequest();
        UrlGenByGoodsIdRequest convert = new UrlGenByGoodsIdRequest();
        VipGoodsClient.bind(search, "u_prod_48843767");
        VipGoodsClient.bind(detail, "u_prod_48843767");
        VipGoodsClient.bind(convert, "u_prod_48843767");
        assertEquals("default_pid", search.getChanTag());
        assertEquals("u_prod_48843767", search.getOpenId());
        assertEquals("default_pid", detail.getChanTag());
        assertEquals("u_prod_48843767", detail.getOpenId());
        assertEquals("u_prod_48843767", convert.getOpenId());
    }

    @Test
    void blankPidDoesNotOpenAClient() {
        SdkSessions.opened.set(0);
        assertThrows(PlatformCallException.class, () -> PddGoodsClient.bind(new PddDdkGoodsSearchRequest(), " ", USER));
        assertEquals(0, SdkSessions.opened.get());
    }

    @Test
    void redisHitSkipsTheTableAndAMissIsCached() {
        Map<String, String> redis = new HashMap<>();
        redis.put("union:relation:" + USER + ":jd", "501");
        AtomicInteger loads = new AtomicInteger();
        RelationLookup table = (userId, platform) -> {
            loads.incrementAndGet();
            return Optional.of("from-db");
        };
        CachingUnionRelation relation = new CachingUnionRelation(new MapCache(redis), table);
        assertEquals("501", relation.find(USER, "jd").orElseThrow());
        assertEquals(0, loads.get());

        redis.clear();
        assertEquals("from-db", relation.find(USER, "vip").orElseThrow());
        assertEquals(1, loads.get());
        assertEquals("from-db", redis.get("union:relation:" + USER + ":vip"));
        assertEquals("from-db", relation.find(USER, "vip").orElseThrow());
        assertEquals(1, loads.get());
    }

    @Test
    void missingRelationFailsBeforeThePlatformCall() {
        SdkSessions.opened.set(0);
        GoodsQueryService query = new GoodsQueryService(
                new MemoryGoods(),
                List.of(new PddGoodsClient("client", "secret")),
                new MemoryGoods(),
                new MemoryGoods(),
                new ObjectMapper(),
                (userId, platform) -> Optional.empty());
        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> query.search(USER, "pdd", "耳机"));
        assertEquals(ErrorCodes.NEED_AUTH, ex.code());
        assertEquals("NEED_AUTH", ex.reason());
        assertFalse(ex.getMessage().contains("501"));
        assertEquals(0, SdkSessions.opened.get());
    }

    private static String fields(Object value) throws Exception {
        ObjectMapper json = new ObjectMapper();
        json.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        return json.writeValueAsString(value);
    }

    private static final class MapCache implements TextCache {
        private final Map<String, String> values;

        private MapCache(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void put(String key, String value, Duration ttl) {
            values.put(key, value);
        }
    }
}
