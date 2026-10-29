package com.omino.shopai.admin.adapter.pay;

import com.omino.shopai.support.withdrawal.PayoutPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
/**
 * 管理端打款适配。本阶段不发起真实转账。
 */

@Component
public class AlipayPayoutClient implements PayoutPort {

    private final String appId;
    private final String privateKey;

    public AlipayPayoutClient(
            @Value("${PAY_ALIPAY_APP_ID:}") String appId,
            @Value("${PAY_ALIPAY_PRIVATE_KEY:}") String privateKey) {
        this.appId = appId;
        this.privateKey = privateKey;
    }
/**
 * 本阶段不打真实渠道，固定返回失败，冻结额由状态机保持不动。
 */

    @Override
    public Result transfer(String withdrawalId, BigDecimal amount, String payeeAccount) {
        String outBizNo = withdrawalId;
        boolean payable = !appId.isBlank()
                && !privateKey.isBlank()
                && outBizNo != null
                && !outBizNo.isBlank()
                && amount != null
                && amount.signum() > 0
                && payeeAccount != null
                && !payeeAccount.isBlank();
        if (!payable) {
            return Result.failed();
        }
        return Result.failed();
    }
}
