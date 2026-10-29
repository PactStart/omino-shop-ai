package com.omino.shopai.app.service.order;

import java.util.List;
/**
 * 一页订单。
 */

public record OrderPage(List<OrderView> list, int page, int size, long total) {
}
