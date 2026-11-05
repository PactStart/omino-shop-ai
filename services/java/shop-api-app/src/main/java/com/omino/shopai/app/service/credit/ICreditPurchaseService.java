package com.omino.shopai.app.service.credit;

import com.omino.shopai.entity.CreditLedger;
import com.omino.shopai.entity.CreditOrder;

import java.util.List;

/**
 * 用返佣余额或第三方支付购买积分。
 */
public interface ICreditPurchaseService {

    /**
     * 创建积分订单。钱包渠道当场扣款入账，其它渠道保持待支付。
     *
     * @param userId 用户主键
     * @param packageId 套餐主键
     * @param channel 支付渠道
     * @param clientOrderId 客户端订单号，用来挡住重复下单
     * @return 已存在或新建的积分订单
     */
    CreditOrder place(String userId, String packageId, String channel, String clientOrderId);

    /**
     * 支付回调入账，同一单只加一次积分。
     *
     * @param orderId 积分订单主键
     * @param txnId 支付流水号
     * @param verified 回调是否已验签
     * @return 验签通过且订单存在时为真；验签失败为假
     */
    boolean settle(String orderId, String txnId, boolean verified);

    /**
     * 列出该用户的积分流水，新的在前。
     *
     * @param userId 用户主键
     * @return 积分流水
     */
    List<CreditLedger> ledger(String userId);

    /**
     * 按主键读取积分订单。不核对属主，调用方自行比对用户。
     *
     * @param id 积分订单主键
     * @return 积分订单；没有则空
     */
    CreditOrder findOrder(String id);
}
