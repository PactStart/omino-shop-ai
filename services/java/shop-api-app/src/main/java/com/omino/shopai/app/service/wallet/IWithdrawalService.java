package com.omino.shopai.app.service.wallet;

import com.omino.shopai.app.dto.WalletViewDto;
import com.omino.shopai.app.dto.WithdrawalViewDto;
import com.omino.shopai.dto.PageDto;

/**
 * 用户侧返佣钱包和提现申请。
 */
public interface IWithdrawalService {

    /**
     * 读取钱包余额。没有钱包时三个金额都是 0.00。
     *
     * @param userId 用户主键
     * @return 可用、冻结和累计收益
     */
    WalletViewDto wallet(String userId);

    /**
     * 申请提现并冻结可用余额。不在这里打款。
     *
     * @param userId 用户主键
     * @param amountText 金额文本，必须是正数且最多两位小数
     * @param payeeAccount 收款账号
     * @param channel 渠道；只接受支付宝，空则按支付宝
     * @return 新建的提现单
     */
    WithdrawalViewDto apply(String userId, String amountText, String payeeAccount, String channel);

    /**
     * 分页列出该用户的提现单。
     *
     * @param userId 用户主键
     * @param page 页码，从 1 起
     * @param size 页大小，最大 50
     * @return 提现单页
     */
    PageDto<WithdrawalViewDto> list(String userId, int page, int size);

    /**
     * 读取该用户的一笔提现。不属于该用户时按不存在处理。
     *
     * @param userId 用户主键
     * @param id 提现单主键
     * @return 提现单
     */
    WithdrawalViewDto get(String userId, String id);
}
