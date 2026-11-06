package com.omino.shopai.admin.web;

import com.omino.shopai.admin.service.mall.AdminMallService;
import com.omino.shopai.admin.web.dto.MallCategoryRequestDto;
import com.omino.shopai.admin.web.dto.MallEntryRequestDto;
import com.omino.shopai.admin.web.dto.MallZonePatchDto;
import com.omino.shopai.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AdminMallController 提供 HTTP 接口。
 */
@RestController
@Tag(name = "AdminMallController")
@RequestMapping("/v1/mall")
public class AdminMallController {

    private final AdminMallService mall;

    /**
     * 注入商城布局用例。
     *
     * @param mall 布局用例
     */
    public AdminMallController(AdminMallService mall) {
        this.mall = mall;
    }

    /**
     * 列出轮播和金刚区两个版位。
     *
     * @return 版位信封
     */
    @Operation(summary = "列出商城版位")
    @GetMapping("/zones")
    public ApiResponse<List<AdminMallService.MallZoneView>> zones() {
        return ApiResponse.ok(mall.zones());
    }

    /**
     * 修改一个版位的轮播间隔或每行个数。
     *
     * @param code 版位码
     * @param request 要改的参数
     * @return 更新后的版位信封
     */
    @Operation(summary = "修改商城版位")
    @PatchMapping("/zones/{code}")
    public ApiResponse<AdminMallService.MallZoneView> updateZone(@PathVariable String code, @RequestBody MallZonePatchDto request) {
        return ApiResponse.ok(mall.updateZone(code, request));
    }

    /**
     * 列出金刚分类。
     *
     * @return 分类信封
     */
    @Operation(summary = "列出商城分类")
    @GetMapping("/categories")
    public ApiResponse<List<AdminMallService.MallCategoryView>> categories() {
        return ApiResponse.ok(mall.categories());
    }

    /**
     * 新建一个金刚分类。
     *
     * @param request 名称、排序和状态
     * @return 新建的分类信封
     */
    @Operation(summary = "新建商城分类")
    @PostMapping("/categories")
    public ApiResponse<AdminMallService.MallCategoryView> createCategory(@RequestBody MallCategoryRequestDto request) {
        return ApiResponse.ok(mall.createCategory(request));
    }

    /**
     * 修改一个金刚分类。
     *
     * @param id 分类主键
     * @param request 名称、排序和状态
     * @return 更新后的分类信封
     */
    @Operation(summary = "修改商城分类")
    @PatchMapping("/categories/{id}")
    public ApiResponse<AdminMallService.MallCategoryView> updateCategory(
            @PathVariable String id, @RequestBody MallCategoryRequestDto request) {
        return ApiResponse.ok(mall.updateCategory(id, request));
    }

    /**
     * 列出商城条目，可以只看一个版位。
     *
     * @param zone 版位码，可空
     * @return 条目信封
     */
    @Operation(summary = "列出商城条目")
    @GetMapping("/entries")
    public ApiResponse<List<AdminMallService.MallEntryView>> entries(@RequestParam(required = false) String zone) {
        return ApiResponse.ok(mall.entries(zone));
    }

    /**
     * 新建一条轮播图或金刚图标。
     *
     * @param request 条目内容
     * @return 新建的条目信封
     */
    @Operation(summary = "新建商城条目")
    @PostMapping("/entries")
    public ApiResponse<AdminMallService.MallEntryView> createEntry(@RequestBody MallEntryRequestDto request) {
        return ApiResponse.ok(mall.createEntry(request));
    }

    /**
     * 修改一条轮播图或金刚图标。
     *
     * @param id 条目主键
     * @param request 条目内容
     * @return 更新后的条目信封
     */
    @Operation(summary = "修改商城条目")
    @PatchMapping("/entries/{id}")
    public ApiResponse<AdminMallService.MallEntryView> updateEntry(
            @PathVariable String id, @RequestBody MallEntryRequestDto request) {
        return ApiResponse.ok(mall.updateEntry(id, request));
    }
}
