package com.omino.shopai.app.web.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.dto.GoodsDetailDto;
import com.omino.shopai.app.service.goods.InternalGoodsService;
import com.omino.shopai.app.web.internal.dto.DetailRequestDto;
import com.omino.shopai.app.web.internal.dto.GoodsSearchRequestDto;
import com.omino.shopai.app.web.internal.dto.ImageRequestDto;
import com.omino.shopai.app.web.internal.dto.LinkRequestDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
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
    /**
     * 按关键词在指定平台搜索商品。条数过多时收成合集。
     *
     * @param userId 当前对话用户
     * @param request 平台和关键词
     * @return 商品或合集信封
     */
    @Operation(summary = "按关键词搜索商品")
    @PostMapping("/search")
    public ApiResponse<Map<String, Object>> search(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody GoodsSearchRequestDto request) {
        return ApiResponse.ok(goods.search(userId, request.platform(), request.keyword(), request.keywords(), request.platforms()));
    }
    /**
     * 从商品链接识别平台和商品，再取详情。
     *
     * @param userId 当前对话用户
     * @param request 商品链接
     * @return 详情信封
     */
    @Operation(summary = "从商品链接识别商品")
    @PostMapping("/parse-link")
    public ApiResponse<GoodsDetailDto> parseLink(@RequestHeader("X-User-Id") String userId, @RequestBody LinkRequestDto request) {
        return ApiResponse.ok(goods.parseLink(userId, request.sourceUrl()));
    }
    /**
     * 从已上传图片识别商品。
     *
     * @param request 图片对象键
     * @return 详情信封
     */
    @Operation(summary = "从图片识别商品")
    @PostMapping("/parse-image")
    public ApiResponse<GoodsDetailDto> parseImage(@RequestBody ImageRequestDto request) {
        return ApiResponse.ok(goods.parseImage(request.imageObjectKey()));
    }
    /**
     * 按商品号或平台商品号读取详情。
     *
     * @param userId 当前对话用户
     * @param request 商品定位
     * @return 详情信封
     */
    @Operation(summary = "读取商品详情")
    @PostMapping("/detail")
    public ApiResponse<GoodsDetailDto> detail(@RequestHeader("X-User-Id") String userId, @RequestBody DetailRequestDto request) {
        return ApiResponse.ok(goods.detail(userId, request.goodsId(), request.platform(), request.platformGoodsId()));
    }
}
