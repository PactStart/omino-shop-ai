package com.omino.shopai.app.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.service.goods.CollectionView;
import com.omino.shopai.app.service.goods.ConvertedLink;
import com.omino.shopai.app.service.goods.GoodsCard;
import com.omino.shopai.app.service.goods.GoodsConvertLinkService;
import com.omino.shopai.app.service.goods.GoodsDetail;
import com.omino.shopai.app.service.goods.GoodsLibraryService;
import com.omino.shopai.app.service.goods.GoodsPage;
import com.omino.shopai.app.service.goods.GoodsQueryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
/**
 * GoodsController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "GoodsController")
@RequestMapping("/v1/goods")
public class GoodsController {

    private final GoodsQueryService query;
    private final GoodsConvertLinkService convert;
    private final GoodsLibraryService library;
/**
 * 注入查询、转链和库。控制器不碰联盟 SDK。
 */

    public GoodsController(GoodsQueryService query, GoodsConvertLinkService convert, GoodsLibraryService library) {
        this.query = query;
        this.convert = convert;
        this.library = library;
    }

    @Operation(summary = "提交 /detail")
    @PostMapping("/detail")
    public ApiResponse<GoodsDetail> detail(@RequestAttribute("userId") String userId, @RequestBody DetailRequest request) {
        return ApiResponse.ok(
                query.detail(userId, request.goodsId(), request.platform(), request.platformGoodsId()),
                traceId(),
                now());
    }

    @Operation(summary = "提交 /convert-link")
    @PostMapping("/convert-link")
    public ApiResponse<ConvertedLink> convertLink(
            @RequestAttribute("userId") String userId,
            @RequestBody ConvertRequest request) {
        return ApiResponse.ok(
                convert.convert(userId, request.goodsId(), request.platform(), request.platformGoodsId(), request.preferApp()),
                traceId(),
                now());
    }

    @Operation(summary = "查询 /history")
    @GetMapping("/history")
    public ApiResponse<GoodsPage<GoodsCard>> history(
            @RequestAttribute("userId") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(library.history(userId, page, size), traceId(), now());
    }

    @Operation(summary = "查询 /favorites")
    @GetMapping("/favorites")
    public ApiResponse<GoodsPage<GoodsCard>> favorites(
            @RequestAttribute("userId") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(library.favorites(userId, page, size), traceId(), now());
    }
/**
 * 把当前用户的收藏写入库。重复收藏保持幂等。
 */

    @Operation(summary = "提交 /favorites")
    @PostMapping("/favorites")
    public ApiResponse<Void> favorite(@RequestAttribute("userId") String userId, @RequestBody FavoriteRequest request) {
        library.favorite(userId, request.goodsId());
        return ApiResponse.ok(null, traceId(), now());
    }
/**
 * 取消收藏。本来就没有也按成功返回。
 */

    @Operation(summary = "删除 /favorites/{goodsId}")
    @DeleteMapping("/favorites/{goodsId}")
    public ApiResponse<Void> unfavorite(@RequestAttribute("userId") String userId, @PathVariable String goodsId) {
        library.unfavorite(userId, goodsId);
        return ApiResponse.ok(null, traceId(), now());
    }

    @Operation(summary = "查询 /collections/{id}")
    @GetMapping("/collections/{id}")
    public ApiResponse<CollectionView> collection(@RequestAttribute("userId") String userId, @PathVariable String id) {
        return ApiResponse.ok(library.collection(userId, id), traceId(), now());
    }
/**
 * 移除。
 */

    @Operation(summary = "删除 /collections/{id}/items/{goodsId}")
    @DeleteMapping("/collections/{id}/items/{goodsId}")
    public ApiResponse<Void> removeCollectionItem(
            @RequestAttribute("userId") String userId,
            @PathVariable String id,
            @PathVariable String goodsId) {
        library.removeCollectionItem(userId, id, goodsId);
        return ApiResponse.ok(null, traceId(), now());
    }

    private static String traceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String now() {
        return OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString();
    }
/**
 * 要看的商品。平台由商品号或字段决定。
 */

    public record DetailRequest(String goodsId, String platform, String platformGoodsId) {
    }
/**
 * 转链目标。未授权时由服务返回 40301。
 */

    public record ConvertRequest(String goodsId, String platform, String platformGoodsId, Boolean preferApp) {
    }
/**
 * 要收藏的商品。
 */

    public record FavoriteRequest(String goodsId) {
    }
}
