package com.omino.shopai.app.goods;

import com.omino.shopai.api.ApiBusinessException;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.adapter.platform.JdGoodsClient;
import com.omino.shopai.app.adapter.platform.PddGoodsClient;
import com.omino.shopai.app.adapter.platform.SdkSessions;
import com.omino.shopai.app.adapter.platform.TaobaoGoodsClient;
import com.omino.shopai.app.adapter.platform.VipGoodsClient;
import com.omino.shopai.app.service.goods.ConvertedLink;
import com.omino.shopai.app.service.goods.GoodsConvertLinkService;
import com.omino.shopai.app.service.goods.LinkIssuer;
import com.omino.shopai.app.service.goods.ServerLinkIssuer;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.enums.Platform;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoodsConvertGateTest {

    private static final String USER = "01USER0000000000000000000A";

    @Test
    void unauthorizedDoesNotConvertEvenWhenKeysExist() {
        MemoryGoods goods = new MemoryGoods();
        AtomicInteger calls = new AtomicInteger();
        GoodsConvertLinkService service = new GoodsConvertLinkService(goods, goods, (snapshot, openInApp, userId, promotionId) -> {
            calls.incrementAndGet();
            return new ConvertedLink(snapshot.getId(), "taobao", "https://example.test/buy", null, null, "https://example.test/buy", "2026-10-30T18:00:00+08:00");
        }, (userId, platform) -> Optional.empty());
        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> service.convert(USER, null, "taobao", "100", true));
        assertEquals(ErrorCodes.NEED_AUTH, ex.code());
        assertEquals("NEED_AUTH", ex.reason());
        assertEquals(0, calls.get());
        assertEquals(0, goods.clicks(USER));
    }

    @Test
    void authorizedEmptyKeysFailBeforeOutboundAndDoNotClick() throws Exception {
        MemoryGoods goods = seeded();
        goods.relation(USER, "taobao", "authorized", null);
        SdkSessions.opened.set(0);
        GoodsConvertLinkService service = new GoodsConvertLinkService(goods, goods, new ServerLinkIssuer(List.of(
                new TaobaoGoodsClient("", ""),
                new JdGoodsClient("", ""),
                new PddGoodsClient("", ""),
                new VipGoodsClient("", ""))), (userId, platform) -> Optional.of("2552715009"));
        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> service.convert(USER, "g1", "taobao", "100", true));
        assertEquals(ErrorCodes.UPSTREAM_PLATFORM, ex.code());
        assertFalse(ex.getMessage().contains("SECRET"));
        assertEquals(0, goods.clicks(USER));
        assertEquals(0, SdkSessions.opened.get());
        String issuer = Files.readString(Path.of("src/main/java/com/omino/shopai/app/service/goods/ServerLinkIssuer.java"));
        assertFalse(issuer.contains("openapp."));
    }

    @Test
    void successfulIssueWritesTheClick() {
        MemoryGoods goods = seeded();
        goods.relation(USER, "taobao", "authorized", null);
        LinkIssuer issuer = (snapshot, openInApp, userId, promotionId) -> new ConvertedLink(
                snapshot.getId(), "taobao", "https://example.test/buy", null, null, "https://example.test/buy", "2026-10-30T18:00:00+08:00");
        GoodsConvertLinkService service = new GoodsConvertLinkService(goods, goods, issuer,
                (userId, platform) -> Optional.of("2552715009"));
        service.convert(USER, "g1", "taobao", "100", false);
        assertEquals(1, goods.clicks(USER));
    }

    private static MemoryGoods seeded() {
        MemoryGoods goods = new MemoryGoods();
        GoodsSnapshot snapshot = new GoodsSnapshot();
        snapshot.setId("g1");
        snapshot.setPlatform(Platform.TAOBAO);
        snapshot.setPlatformGoodsId("100");
        snapshot.setTitle("耳机");
        goods.save(snapshot);
        return goods;
    }
}
