package com.omino.shopai.app.adapter;

import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.adapter.platform.JdGoodsClient;
import com.omino.shopai.app.adapter.platform.SdkSessions;
import com.omino.shopai.app.adapter.platform.PddGoodsClient;
import com.omino.shopai.app.adapter.platform.PlatformGoodsClient;
import com.omino.shopai.app.adapter.platform.TaobaoGoodsClient;
import com.omino.shopai.app.adapter.platform.VipGoodsClient;
import com.omino.shopai.app.goods.MemoryGoods;
import com.omino.shopai.app.service.goods.GoodsQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlatformClientTest {

    private static final String PACKAGE = "com.omino.shopai.app.adapter.platform";
    private static final String SECRET = "SECRET_UNION_KEY";

    @Test
    void clientsLiveInThePlatformAdapterPackage() {
        assertEquals(PACKAGE, PlatformGoodsClient.class.getPackageName());
        assertEquals(PACKAGE, TaobaoGoodsClient.class.getPackageName());
        assertEquals(PACKAGE, JdGoodsClient.class.getPackageName());
        assertEquals(PACKAGE, PddGoodsClient.class.getPackageName());
        assertEquals(PACKAGE, VipGoodsClient.class.getPackageName());
    }

    @Test
    void missingKeysFailAs50010WithoutOpeningOfficialClients() {
        SdkSessions.opened.set(0);
        GoodsQueryService missing = query(List.of(
                new TaobaoGoodsClient("", ""),
                new JdGoodsClient("", ""),
                new PddGoodsClient("", ""),
                new VipGoodsClient("", "")));
        for (String platform : List.of("taobao", "jd", "pdd", "vip")) {
            assertUpstream(missing, platform);
        }
        assertEquals(0, SdkSessions.opened.get());
    }

    @Test
    void meituanIsRejected() {
        GoodsQueryService query = query(List.of(
                new TaobaoGoodsClient("", ""),
                new JdGoodsClient("", ""),
                new PddGoodsClient("", ""),
                new VipGoodsClient("", "")));
        ApiBusinessException detail = assertThrows(ApiBusinessException.class,
                () -> query.detail("01USER0000000000000000000A", null, "meituan", "1"));
        ApiBusinessException search = assertThrows(ApiBusinessException.class,
                () -> query.search("meituan", "耳机"));
        assertEquals(ErrorCodes.VALIDATION, detail.code());
        assertEquals(ErrorCodes.VALIDATION, search.code());
        assertFalse(detail.getMessage().contains(SECRET));
    }

    @Test
    void queryServiceDoesNotConstructUnionSdks() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/omino/shopai/app/service/goods/GoodsQueryService.java"));
        assertFalse(source.contains("new Taobao"));
        assertFalse(source.contains("new Jd"));
        assertFalse(source.contains("new Pdd"));
        assertFalse(source.contains("new Vip"));
    }

    private static void assertUpstream(GoodsQueryService query, String platform) {
        ApiBusinessException search = assertThrows(ApiBusinessException.class, () -> query.search(platform, "耳机"));
        ApiBusinessException detail = assertThrows(ApiBusinessException.class,
                () -> query.detail("01USER0000000000000000000A", null, platform, "100"));
        assertEquals(ErrorCodes.UPSTREAM_PLATFORM, search.code());
        assertEquals(ErrorCodes.UPSTREAM_PLATFORM, detail.code());
        assertFalse(search.getMessage().contains(SECRET));
        assertFalse(detail.getMessage().contains(SECRET));
    }

    private static GoodsQueryService query(List<PlatformGoodsClient> clients) {
        MemoryGoods goods = new MemoryGoods();
        return new GoodsQueryService(goods, clients, goods, goods, new ObjectMapper());
    }
}
