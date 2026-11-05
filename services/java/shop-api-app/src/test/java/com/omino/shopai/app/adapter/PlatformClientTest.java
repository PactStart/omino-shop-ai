package com.omino.shopai.app.adapter;

import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.adapter.platform.JdGoodsImpl;
import com.omino.shopai.app.util.SdkSessions;
import com.omino.shopai.app.adapter.platform.PddGoodsImpl;
import com.omino.shopai.app.adapter.platform.IPlatformGoods;
import com.omino.shopai.app.adapter.platform.TaobaoGoodsImpl;
import com.omino.shopai.app.adapter.platform.VipGoodsImpl;
import com.omino.shopai.app.goods.MemoryGoods;
import com.omino.shopai.app.goods.MemoryGoodsQuery;
import com.omino.shopai.app.service.goods.IGoodsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlatformClientTest {

    private static final String PACKAGE = "com.omino.shopai.app.adapter.platform";
    private static final String SECRET = "SECRET_UNION_KEY";

    @Test
    void clientsLiveInThePlatformAdapterPackage() {
        assertEquals(PACKAGE, IPlatformGoods.class.getPackageName());
        assertEquals(PACKAGE, TaobaoGoodsImpl.class.getPackageName());
        assertEquals(PACKAGE, JdGoodsImpl.class.getPackageName());
        assertEquals(PACKAGE, PddGoodsImpl.class.getPackageName());
        assertEquals(PACKAGE, VipGoodsImpl.class.getPackageName());
    }

    @Test
    void missingKeysFailAs50010WithoutOpeningOfficialClients() {
        SdkSessions.opened.set(0);
        IGoodsService missing = query(List.of(
                new TaobaoGoodsImpl("", ""),
                new JdGoodsImpl("", ""),
                new PddGoodsImpl("", ""),
                new VipGoodsImpl("", "")));
        for (String platform : List.of("taobao", "jd", "pdd", "vip")) {
            assertUpstream(missing, platform);
        }
        assertEquals(0, SdkSessions.opened.get());
    }

    @Test
    void meituanIsRejected() {
        IGoodsService query = query(List.of(
                new TaobaoGoodsImpl("", ""),
                new JdGoodsImpl("", ""),
                new PddGoodsImpl("", ""),
                new VipGoodsImpl("", "")));
        ApiBusinessException detail = assertThrows(ApiBusinessException.class,
                () -> query.detail("01USER0000000000000000000A", null, "meituan", "1"));
        ApiBusinessException search = assertThrows(ApiBusinessException.class,
                () -> query.search("01USER0000000000000000000A", "meituan", "耳机"));
        assertEquals(ErrorCodes.VALIDATION, detail.code());
        assertEquals(ErrorCodes.VALIDATION, search.code());
        assertFalse(detail.getMessage().contains(SECRET));
    }

    @Test
    void queryServiceDoesNotConstructUnionSdks() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/omino/shopai/app/service/goods/GoodsServiceImpl.java"));
        assertFalse(source.contains("new Taobao"));
        assertFalse(source.contains("new Jd"));
        assertFalse(source.contains("new Pdd"));
        assertFalse(source.contains("new Vip"));
    }

    private static void assertUpstream(IGoodsService query, String platform) {
        ApiBusinessException search = assertThrows(ApiBusinessException.class,
                () -> query.search("01USER0000000000000000000A", platform, "耳机"));
        ApiBusinessException detail = assertThrows(ApiBusinessException.class,
                () -> query.detail("01USER0000000000000000000A", null, platform, "100"));
        assertEquals(ErrorCodes.UPSTREAM_PLATFORM, search.code());
        assertEquals(ErrorCodes.UPSTREAM_PLATFORM, detail.code());
        assertFalse(search.getMessage().contains(SECRET));
        assertFalse(detail.getMessage().contains(SECRET));
    }

    private static IGoodsService query(List<IPlatformGoods> clients) {
        MemoryGoods goods = new MemoryGoods();
        String userId = "01USER0000000000000000000A";
        goods.relation(userId, "taobao", "authorized", "1");
        goods.relation(userId, "jd", "authorized", "1");
        goods.relation(userId, "pdd", "authorized", "1");
        goods.relation(userId, "vip", "authorized", "1");
        return new MemoryGoodsQuery(goods, clients, new ObjectMapper());
    }
}
