package com.omino.shopai.app.goods;

import com.omino.shopai.enums.Platform;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.app.adapter.platform.IPlatformGoods;
import com.omino.shopai.app.dto.PromotionLinkDto;
import com.omino.shopai.dto.order.IncomingOrderDto;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.app.service.goods.GoodsConvertLinkService;
import com.omino.shopai.app.service.goods.GoodsQueryService;
import com.omino.shopai.app.service.goods.LinkIssuerImpl;
import com.omino.shopai.app.web.app.GoodsController;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.support.Ids;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoodsGateTest {

    private static final String USER = "01USER0000000000000000000A";
    private static final String SECRET = "SECRET_RELATION_99";

    private MemoryGoods goods;
    private MockMvc mvc;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        goods = new MemoryGoods();
        goods.relation(USER, "jd", "none", SECRET);
        goods.relation(USER, "taobao", "none", SECRET);
        ObjectMapper json = new ObjectMapper();
        JdCatalog catalog = new JdCatalog();
        GoodsQueryService query = new GoodsQueryService(goods, List.of(catalog), goods, goods, json, goods);
        GoodsConvertLinkService convert = new GoodsConvertLinkService(goods, goods, new LinkIssuerImpl(List.of(catalog)));
        mvc = MockMvcBuilders.standaloneSetup(new GoodsController(query, convert, new com.omino.shopai.app.service.goods.GoodsLibraryService(goods, goods)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        logger = (Logger) LoggerFactory.getLogger("com.omino.shopai");
        logger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    void unauthorizedConvertIsNeedAuthAndHidesPromotionIds() throws Exception {
        String body = mvc.perform(post("/v1/goods/convert-link")
                        .requestAttr("userId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"taobao\",\"platformGoodsId\":\"100012345678\",\"preferApp\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301))
                .andExpect(jsonPath("$.error.reason").value("NEED_AUTH"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertNoPromotionId(body);
        assertNoPromotionId(logged());
        assertEquals(0, goods.clicks(USER));
    }

    @Test
    void unknownSalesAndReviewsStayNull() throws Exception {
        mvc.perform(post("/v1/goods/detail")
                        .requestAttr("userId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"jd\",\"platformGoodsId\":\"100012345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.salesCount").value(nullValue()))
                .andExpect(jsonPath("$.data.reviewCount").value(nullValue()));

        mvc.perform(post("/v1/goods/detail")
                        .requestAttr("userId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"jd\",\"platformGoodsId\":\"100012345678\"}"))
                .andExpect(jsonPath("$.data.salesCount").value(nullValue()))
                .andExpect(jsonPath("$.data.reviewCount").value(nullValue()));
        assertEquals(2, goods.history(USER, 1, 20).total());
    }

    @Test
    void authorizedConvertWritesClickWithoutPromotionId() throws Exception {
        GoodsSnapshot saved = new GoodsSnapshot();
        saved.setId(Ids.ulid());
        saved.setPlatform(Platform.JD);
        saved.setPlatformGoodsId("100012345678");
        saved.setTitle("示例降噪耳机");
        saved.setGoodsUrl("https://item.jd.com/100012345678.html");
        goods.save(saved);
        goods.relation(USER, "jd", "authorized", SECRET);

        String body = mvc.perform(post("/v1/goods/convert-link")
                        .requestAttr("userId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goodsId\":\"" + saved.getId() + "\",\"platform\":\"jd\",\"platformGoodsId\":\"100012345678\",\"preferApp\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deeplink").value(nullValue()))
                .andExpect(jsonPath("$.data.universalLink").value("https://item.jd.com/100012345678.html"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertNoPromotionId(body);
        assertNoPromotionId(logged());
        assertEquals(1, goods.clicks(USER));
    }

    private String logged() {
        StringBuilder text = new StringBuilder();
        for (ILoggingEvent event : logs.list) {
            text.append(event.getFormattedMessage());
        }
        return text.toString();
    }

    private static void assertNoPromotionId(String text) {
        assertFalse(text.contains(SECRET));
        assertFalse(text.contains("\"relationId\""));
        assertFalse(text.contains("\"relation_id\""));
        assertFalse(text.contains("\"pid\""));
        assertFalse(text.contains("\"PID\""));
    }

    private static final class JdCatalog implements IPlatformGoods {
        @Override
        /**
         * 这个假目录只回答京东。
         *
         * @return 京东
         */
        public Platform platform() {
            return Platform.JD;
        }

        @Override
        public GoodsSnapshot detail(String platformGoodsId, String userId, String promotionId) {
            GoodsSnapshot snapshot = new GoodsSnapshot();
            snapshot.setPlatform(Platform.JD);
            snapshot.setPlatformGoodsId(platformGoodsId);
            snapshot.setTitle("示例降噪耳机");
            snapshot.setPrice(new BigDecimal("799.00"));
            snapshot.setCouponPrice(new BigDecimal("749.00"));
            snapshot.setGoodsUrl("https://item.jd.com/" + platformGoodsId + ".html");
            return snapshot;
        }

        @Override
        public List<GoodsSnapshot> search(String keyword, String userId, String promotionId) {
            return List.of();
        }

        @Override
        public PromotionLinkDto convert(GoodsSnapshot goods, boolean openInApp, String userId, String promotionId) {
            return new PromotionLinkDto(goods.getGoodsUrl(), null, goods.getGoodsUrl(), goods.getGoodsUrl());
        }

        @Override
        public List<IncomingOrderDto> pullOrders(
                OrderSyncCursor cursor, com.omino.shopai.app.util.UnionMaps.Window created,
                com.omino.shopai.app.util.UnionMaps.Window updated) {
            return List.of();
        }
    }
}
