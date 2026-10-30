package com.omino.shopai.app.web.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.service.goods.GoodsDetail;
import com.omino.shopai.app.service.goods.InternalGoodsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/**
 * InternalGoodsController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "InternalGoodsController")
@RequestMapping("/internal/v1/goods")
public class InternalGoodsController {

    private final InternalGoodsService goods;

    public InternalGoodsController(InternalGoodsService goods) {
        this.goods = goods;
    }

    @Operation(summary = "提交 /search")
    @PostMapping("/search")
    public ApiResponse<Map<String, Object>> search(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody SearchRequest request) {
        return ApiResponse.ok(goods.search(userId, request.platform(), request.keyword(), request.keywords(), request.platforms()), trace(), now());
    }

    @Operation(summary = "提交 /parse-link")
    @PostMapping("/parse-link")
    public ApiResponse<GoodsDetail> parseLink(@RequestHeader("X-User-Id") String userId, @RequestBody LinkRequest request) {
        return ApiResponse.ok(goods.parseLink(userId, request.sourceUrl()), trace(), now());
    }

    @Operation(summary = "提交 /parse-image")
    @PostMapping("/parse-image")
    public ApiResponse<GoodsDetail> parseImage(@RequestBody ImageRequest request) {
        return ApiResponse.ok(goods.parseImage(request.imageObjectKey()), trace(), now());
    }

    @Operation(summary = "提交 /detail")
    @PostMapping("/detail")
    public ApiResponse<GoodsDetail> detail(@RequestHeader("X-User-Id") String userId, @RequestBody DetailRequest request) {
        return ApiResponse.ok(goods.detail(userId, request.goodsId(), request.platform(), request.platformGoodsId()), trace(), now());
    }

    private static String trace() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
/**
 * 搜索词和平台。
 */

    public record SearchRequest(String platform, String keyword, List<String> keywords, List<String> platforms) {
    }
/**
 * 要解析的商品链接。
 */

    public record LinkRequest(String sourceUrl) {
    }
/**
 * 图片地址。本阶段解析固定按不存在返回。
 */

    public record ImageRequest(String imageObjectKey) {
    }
/**
 * 要看的商品。平台由商品号或字段决定。
 */

    public record DetailRequest(String goodsId, String platform, String platformGoodsId) {
    }
}
