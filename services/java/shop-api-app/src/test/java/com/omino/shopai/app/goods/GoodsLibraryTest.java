package com.omino.shopai.app.goods;

import com.omino.shopai.enums.Platform;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ApiExceptionHandler;
import com.omino.shopai.app.service.goods.IGoodsLibraryService;
import com.omino.shopai.app.web.app.GoodsController;
import com.omino.shopai.entity.GoodsCollection;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.support.Ids;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoodsLibraryTest {

    private static final String OWNER = "01USER0000000000000000000A";
    private static final String OTHER = "01USER0000000000000000000B";

    private MemoryGoods goods;
    private IGoodsLibraryService library;
    private MockMvc mvc;
    private String goodsId;
    private String collectionId;

    @BeforeEach
    void setUp() {
        goods = new MemoryGoods();
        GoodsSnapshot snapshot = new GoodsSnapshot();
        snapshot.setId(Ids.ulid());
        snapshot.setPlatform(Platform.JD);
        snapshot.setPlatformGoodsId("100012345678");
        snapshot.setTitle("示例降噪耳机");
        GoodsCollection collection = goods.seedCollection(OWNER, "纸巾推荐", snapshot);
        goodsId = snapshot.getId();
        collectionId = collection.getId();
        library = new MemoryGoodsLibrary(goods);
        MemoryGoodsQuery query = new MemoryGoodsQuery(goods, java.util.List.of(), new ObjectMapper());
        mvc = MockMvcBuilders.standaloneSetup(new GoodsController(query, library))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void favoriteIsIdempotent() throws Exception {
        String body = "{\"goodsId\":\"" + goodsId + "\"}";
        mvc.perform(post("/v1/goods/favorites").requestAttr("userId", OWNER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/v1/goods/favorites").requestAttr("userId", OWNER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(1, library.favoriteCount(OWNER, goodsId));
        mvc.perform(get("/v1/goods/favorites").requestAttr("userId", OWNER))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list.length()").value(1));
    }

    @Test
    void removingAnotherUsersCollectionItemIsForbidden() throws Exception {
        mvc.perform(delete("/v1/goods/collections/" + collectionId + "/items/" + goodsId).requestAttr("userId", OTHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40300));
        mvc.perform(get("/v1/goods/collections/" + collectionId).requestAttr("userId", OWNER))
                .andExpect(jsonPath("$.data.goods.length()").value(1))
                .andExpect(jsonPath("$.data.goods[0].goodsId").value(goodsId));
    }

    @Test
    void removedItemLeavesTheOwnersCollection() throws Exception {
        mvc.perform(delete("/v1/goods/collections/" + collectionId + "/items/" + goodsId).requestAttr("userId", OWNER))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/v1/goods/collections/" + collectionId).requestAttr("userId", OWNER))
                .andExpect(jsonPath("$.data.title").value("纸巾推荐"))
                .andExpect(jsonPath("$.data.goods.length()").value(0));
    }
}
