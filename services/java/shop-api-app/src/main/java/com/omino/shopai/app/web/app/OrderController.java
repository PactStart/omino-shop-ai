package com.omino.shopai.app.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.omino.shopai.api.ApiResponse;
import com.omino.shopai.app.dto.OrderDetailDto;
import com.omino.shopai.app.dto.OrderViewDto;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.app.service.order.IOrderQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OrderController 提供 HTTP 接口。
 */

@RestController
@Tag(name = "OrderController")
@RequestMapping("/v1/orders")
public class OrderController {

    private final IOrderQueryService orders;

    /**
     * 注入订单查询。
     *
     * @param orders 订单查询
     */
    public OrderController(IOrderQueryService orders) {
        this.orders = orders;
    }
    /**
     * 按平台和状态分页列出当前用户的电商订单。
     *
     * @param userId 当前用户
     * @param platform 平台码，可空
     * @param orderStatus 订单状态，可空
     * @param settleStatus 结算状态，可空
     * @param keyword 标题或平台订单号，可空
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 订单分页信封
     */
    @Operation(summary = "列出我的电商订单")
    @GetMapping
    public ApiResponse<PageDto<OrderViewDto>> list(
            @RequestAttribute("userId") String userId,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String settleStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(orders.list(userId, platform, orderStatus, settleStatus, keyword, page, size));
    }
    /**
     * 读取当前用户的一笔电商订单。
     *
     * @param userId 当前用户
     * @param id 订单编号
     * @return 订单详情信封
     */
    @Operation(summary = "读取一笔电商订单")
    @GetMapping("/{id}")
    public ApiResponse<OrderDetailDto> detail(@RequestAttribute("userId") String userId, @PathVariable String id) {
        return ApiResponse.ok(orders.detail(userId, id));
    }


}
