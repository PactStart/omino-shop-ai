package com.omino.shopai.app.service.credit;

import com.omino.shopai.entity.CreditAccount;
import com.omino.shopai.entity.CreditLedger;
import com.omino.shopai.entity.CreditOrder;
import com.omino.shopai.entity.CreditPackage;
import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.WalletLedger;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;
/**
 * 扣费需要的积分读取。
 */

public interface ICreditLedger {

    <T> T exclusively(String userId, Supplier<T> action);

    Wallet wallet(String userId);

    void saveWallet(Wallet wallet);

    void addWalletLedger(WalletLedger ledger);

    CreditAccount creditAccount(String userId);

    void saveCreditAccount(CreditAccount account);

    void addCreditLedger(CreditLedger ledger);

    int purchaseCount(String creditOrderId);

    CreditPackage findPackage(String id);

    CreditOrder findOrder(String id);

    CreditOrder findClientOrder(String userId, String clientOrderId);

    void insertOrder(CreditOrder order);

    void updateOrder(CreditOrder order);

    List<CreditPackage> activePackages();

    List<CreditLedger> ledger(String userId);

    BigDecimal available(String userId);
}
