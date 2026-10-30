package com.omino.shopai.app.service.credit;

import com.omino.shopai.entity.CreditAccount;
import com.omino.shopai.entity.CreditLedger;
import com.omino.shopai.entity.CreditOrder;
import com.omino.shopai.entity.CreditPackage;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;

import java.util.List;
import java.util.function.Supplier;
/**
 * 购买积分需要的账户、订单和流水。
 */

public interface ICredit {

    <T> T exclusively(String userId, Supplier<T> action);

    Wallet wallet(String userId);

    void saveWallet(Wallet wallet);

    void insertWalletLedger(WalletLedger ledger);

    CreditAccount credit(String userId);

    void saveCredit(CreditAccount account);

    CreditPackage findPackage(String id);

    List<CreditPackage> activePackages();

    CreditOrder findByClient(String userId, String clientOrderId);

    CreditOrder findOrder(String id);

    void saveOrder(CreditOrder order);

    void insertLedger(CreditLedger ledger);

    boolean purchaseExists(String creditOrderId);

    List<CreditLedger> ledger(String userId);
}
