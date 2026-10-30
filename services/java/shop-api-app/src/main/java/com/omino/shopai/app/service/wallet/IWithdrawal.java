package com.omino.shopai.app.service.wallet;

import com.omino.shopai.entity.Wallet;
import com.omino.shopai.entity.Withdrawal;

import java.util.List;
import java.util.function.Supplier;
/**
 * 用户侧提现单的存储端口。
 */

public interface IWithdrawal {

    <T> T exclusively(String userId, Supplier<T> action);

    Wallet wallet(String userId);

    void save(Wallet wallet);

    void insert(Withdrawal withdrawal);

    Withdrawal find(String id);

    List<Withdrawal> list(String userId, int page, int size);

    long count(String userId);
}
