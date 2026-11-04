package com.omino.shopai.app.service.order;

import com.omino.shopai.entity.AppOrder;
import com.omino.shopai.entity.OrderSyncCursor;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;

import java.time.OffsetDateTime;
import java.util.List;
/**
 * 订单同步需要的存储端口。
 */

public interface IOrder {

    OrderSyncCursor cursor(String platform);

    /**
     * 把拉完的那段水位推上去，并清掉失败文案。空的那一列保持原值。
     *
     * @param platform 平台码
     * @param createTo 下单水位的新值；空表示这一列本次没拉
     * @param updateTo 更新水位的新值；空表示这一列本次没拉
     */
    void advance(String platform, OffsetDateTime createTo, OffsetDateTime updateTo);

    /** 只写失败文案，不改两列水位。 */
    void fail(String platform, String message);

    AppOrder findByPlatformOrder(String platform, String platformOrderId);

    void save(AppOrder order);

    Wallet lockWallet(String userId);

    void saveWallet(Wallet wallet);

    WalletLedger findCommissionByOrder(String orderId);

    void insertLedger(WalletLedger ledger);

    int commissionCount(String userId);

    List<AppOrder> list(String userId, String platform, String orderStatus, String settleStatus, int page, int size);

    long count(String userId, String platform, String orderStatus, String settleStatus);

    AppOrder findById(String id);
}
