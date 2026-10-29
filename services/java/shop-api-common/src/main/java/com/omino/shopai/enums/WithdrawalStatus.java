package com.omino.shopai.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 提现单状态。 库存的是 code，不是枚举名。 */
public enum WithdrawalStatus {
    PENDING("pending"),
    PAID("paid"),
    REJECTED("rejected");

    @EnumValue
    @JsonValue
    private final String code;

    WithdrawalStatus(String code) {
        this.code = code;
    }

    /** 写入数据库和 JSON 的取值。 */
    public String code() {
        return code;
    }

    /** 把库存或请求里的取值还原成枚举。空串视为未填。无法识别时抛出，避免悄悄落成 null。 */
    @JsonCreator
    public static WithdrawalStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (WithdrawalStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException(code);
    }
}
