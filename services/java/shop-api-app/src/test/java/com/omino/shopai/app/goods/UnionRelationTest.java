package com.omino.shopai.app.goods;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.adapter.platform.PddGoodsImpl;
import com.omino.shopai.exception.PlatformCallException;
import com.omino.shopai.app.util.SdkSessions;
import com.omino.shopai.app.adapter.platform.VipGoodsImpl;
import com.omino.shopai.app.service.goods.GoodsServiceImpl;
import com.omino.shopai.enums.Platform;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkGoodsPromotionUrlGenerateRequest;
import com.pdd.pop.sdk.http.api.pop.request.PddDdkGoodsSearchRequest;
import com.vip.adp.api.open.service.GetByGoodsIdsRequest;
import com.vip.adp.api.open.service.QueryGoodsRequest;
import com.vip.adp.api.open.service.UrlGenByGoodsIdRequest;
import org.junit.jupiter.api.Test;

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
        PddGoodsImpl.bind(search, "501", USER);
        PddDdkGoodsPromotionUrlGenerateRequest convert = new PddDdkGoodsPromotionUrlGenerateRequest();
        PddGoodsImpl.bind(convert, "501", USER);
        String searchJson = fields(search);
        String convertJson = fields(convert);
        assertTrue(searchJson.contains("501"));
        assertTrue(searchJson.contains("uid"));
        assertTrue(searchJson.contains(USER));
        assertTrue(convertJson.contains("501"));
        assertTrue(convertJson.contains("uid"));
        assertTrue(convertJson.contains(USER));
        assertEquals(Platform.JD, Platform.PDD.relationPlatform());
        assertEquals(Platform.TAOBAO, Platform.TAOBAO.relationPlatform());
    }

    @Test
    void vipRelationGoesToOpenIdAndChanTagStaysDefault() {
        QueryGoodsRequest search = new QueryGoodsRequest();
        GetByGoodsIdsRequest detail = new GetByGoodsIdsRequest();
        UrlGenByGoodsIdRequest convert = new UrlGenByGoodsIdRequest();
        VipGoodsImpl.bind(search, "u_prod_48843767");
        VipGoodsImpl.bind(detail, "u_prod_48843767");
        VipGoodsImpl.bind(convert, "u_prod_48843767");
        assertEquals("default_pid", search.getChanTag());
        assertEquals("u_prod_48843767", search.getOpenId());
        assertEquals("default_pid", detail.getChanTag());
        assertEquals("u_prod_48843767", detail.getOpenId());
        assertEquals("u_prod_48843767", convert.getOpenId());
    }

    @Test
    void blankPidDoesNotOpenAClient() {
        SdkSessions.opened.set(0);
        assertThrows(PlatformCallException.class, () -> PddGoodsImpl.bind(new PddDdkGoodsSearchRequest(), " ", USER));
        assertEquals(0, SdkSessions.opened.get());
    }

    @Test
    void redisHitSkipsTheTableAndAMissIsCached() {
        Map<String, String> redis = new HashMap<>();
        redis.put("union:relation:" + USER + ":jd", "501");
        AtomicInteger loads = new AtomicInteger();
        assertEquals("501", GoodsServiceImpl.cached(USER, "jd", redis::get, redis::put, (userId, platform) -> {
            loads.incrementAndGet();
            return Optional.of("from-db");
        }).orElseThrow());
        assertEquals(0, loads.get());

        redis.clear();
        assertEquals("from-db", GoodsServiceImpl.cached(USER, "vip", redis::get, redis::put, (userId, platform) -> {
            loads.incrementAndGet();
            return Optional.of("from-db");
        }).orElseThrow());
        assertEquals(1, loads.get());
        assertEquals("from-db", redis.get("union:relation:" + USER + ":vip"));
        assertEquals("from-db", GoodsServiceImpl.cached(USER, "vip", redis::get, redis::put, (userId, platform) -> {
            loads.incrementAndGet();
            return Optional.of("from-db");
        }).orElseThrow());
        assertEquals(1, loads.get());
    }

    @Test
    void missingRelationFailsBeforeThePlatformCall() {
        SdkSessions.opened.set(0);
        MemoryGoods goods = new MemoryGoods();
        MemoryGoodsQuery query = new MemoryGoodsQuery(
                goods,
                List.of(new PddGoodsImpl("client", "secret")),
                new ObjectMapper());
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
}
