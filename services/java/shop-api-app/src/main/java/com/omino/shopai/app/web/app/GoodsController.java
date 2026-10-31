package com.omino.shopai.app.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.dto.CollectionViewDto;
import com.omino.shopai.app.web.app.dto.ConvertRequestDto;
import com.omino.shopai.app.web.app.dto.DetailRequestDto;
import com.omino.shopai.app.web.app.dto.FavoriteRequestDto;
import com.omino.shopai.app.dto.ConvertedLinkDto;
import com.omino.shopai.app.dto.GoodsCardDto;
import com.omino.shopai.app.service.goods.GoodsConvertLinkService;
import com.omino.shopai.app.dto.GoodsDetailDto;
import com.omino.shopai.app.service.goods.GoodsLibraryService;
import com.omino.shopai.dto.PageDto;
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
    /**
     * 读取商品详情。有商品号时读快照，否则按平台商品号查询。
     *
     * @param userId 当前用户
     * @param request 商品号或平台商品号
     * @return 详情信封
     */
    @Operation(summary = "读取商品详情")
    @PostMapping("/detail")
    public ApiResponse<GoodsDetailDto> detail(@RequestAttribute("userId") String userId, @RequestBody DetailRequestDto request) {
        return ApiResponse.ok(
                query.detail(userId, request.goodsId(), request.platform(), request.platformGoodsId()));
    }
    /**
     * 校验授权和推广身份后生成购买链接。
     *
     * @param userId 当前用户
     * @param request 商品与是否优先打开 APP
     * @return 转链信封
     */
    @Operation(summary = "生成购买链接")
    @PostMapping("/convert-link")
    public ApiResponse<ConvertedLinkDto> convertLink(
            @RequestAttribute("userId") String userId,
            @RequestBody ConvertRequestDto request) {
        return ApiResponse.ok(
                convert.convert(userId, request.goodsId(), request.platform(), request.platformGoodsId(), request.preferApp()));
    }
    /**
     * 分页列出当前用户浏览过的商品。
     *
     * @param userId 当前用户
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 商品卡分页信封
     */
    @Operation(summary = "列出浏览过的商品")
    @GetMapping("/history")
    public ApiResponse<PageDto<GoodsCardDto>> history(
            @RequestAttribute("userId") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(library.history(userId, page, size));
    }
    /**
     * 分页列出当前用户收藏的商品。
     *
     * @param userId 当前用户
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 商品卡分页信封
     */
    @Operation(summary = "列出收藏的商品")
    @GetMapping("/favorites")
    public ApiResponse<PageDto<GoodsCardDto>> favorites(
            @RequestAttribute("userId") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(library.favorites(userId, page, size));
    }
    /**
     * 把一件商品写入当前用户的收藏。重复收藏保持幂等。
     *
     * @param userId 当前用户
     * @param request 商品号
     * @return 空信封
     */
    @Operation(summary = "收藏一件商品")
    @PostMapping("/favorites")
    public ApiResponse<Void> favorite(@RequestAttribute("userId") String userId, @RequestBody FavoriteRequestDto request) {
        library.favorite(userId, request.goodsId());
        return ApiResponse.ok(null);
    }
    /**
     * 取消当前用户对一件商品的收藏。本来就没有也按成功返回。
     *
     * @param userId 当前用户
     * @param goodsId 商品号
     * @return 空信封
     */
    @Operation(summary = "取消收藏")
    @DeleteMapping("/favorites/{goodsId}")
    public ApiResponse<Void> unfavorite(@RequestAttribute("userId") String userId, @PathVariable String goodsId) {
        library.unfavorite(userId, goodsId);
        return ApiResponse.ok(null);
    }
    /**
     * 读取当前用户的一个商品合集。
     *
     * @param userId 当前用户
     * @param id 合集编号
     * @return 合集信封
     */
    @Operation(summary = "读取一个商品合集")
    @GetMapping("/collections/{id}")
    public ApiResponse<CollectionViewDto> collection(@RequestAttribute("userId") String userId, @PathVariable String id) {
        return ApiResponse.ok(library.collection(userId, id));
    }
    /**
     * 从合集里移除一件商品。
     *
     * @param userId 当前用户
     * @param id 合集编号
     * @param goodsId 商品号
     * @return 空信封
     */
    @Operation(summary = "从合集移除一件商品")
    @DeleteMapping("/collections/{id}/items/{goodsId}")
    public ApiResponse<Void> removeCollectionItem(
            @RequestAttribute("userId") String userId,
            @PathVariable String id,
            @PathVariable String goodsId) {
        library.removeCollectionItem(userId, id, goodsId);
        return ApiResponse.ok(null);
    }
}
