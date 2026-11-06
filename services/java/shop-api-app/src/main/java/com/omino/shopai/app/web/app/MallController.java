package com.omino.shopai.app.web.app;

import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.dto.MallHomeDto;
import com.omino.shopai.app.service.mall.IMallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MallController 提供 HTTP 接口。
 */
@RestController
@Tag(name = "MallController")
@RequestMapping("/v1/mall")
public class MallController {

    private final IMallService mall;

    /**
     * 注入商城布局用例。
     *
     * @param mall 布局用例
     */
    public MallController(IMallService mall) {
        this.mall = mall;
    }

    /**
     * 按当前端和渠道读取商城首页的轮播和金刚区。
     *
     * @param clientType 客户端类型
     * @param channel 渠道
     * @return 布局信封
     */
    @Operation(summary = "读取商城首页布局")
    @GetMapping
    public ApiResponse<MallHomeDto> home(
            @RequestHeader("X-Client-Type") String clientType,
            @RequestHeader("X-App-Channel") String channel) {
        return ApiResponse.ok(mall.home(clientType, channel));
    }
}
