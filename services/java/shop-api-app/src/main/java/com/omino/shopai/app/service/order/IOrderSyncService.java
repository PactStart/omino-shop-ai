package com.omino.shopai.app.service.order;

/**
 * 按平台追赶联盟订单并入账。
 */
public interface IOrderSyncService {

    /**
     * 受理一次追赶并立刻返回。同一平台已在追赶时不再并行一轮。失败记下这次的原因。
     *
     * @param platform 平台码
     * @return 无
     */
    void submit(String platform);

    /**
     * 按平台允许的跨度逐段拉到本次请求时间。一段的订单和水位前进在同一事务里，失败则这段不前进。
     *
     * @param platform 平台码
     * @return 无
     */
    void sync(String platform);

    /**
     * 这一平台失败时记下原因。两列水位都不改。
     *
     * @param platform 平台码
     * @param error 这次失败
     * @return 无
     */
    void noteFailure(String platform, Throwable error);
}
