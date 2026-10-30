package com.omino.shopai.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 电商订单状态。 库存的是 code，不是枚举名。 */
public enum OrderStatus {
    PENDING_PAY("pending_pay"),
    PENDING_SHIP("pending_ship"),
    SHIPPED("shipped"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    UNKNOWN("unknown");

    @EnumValue
    @JsonValue
    private final String code;

    OrderStatus(String code) {
        this.code = code;
    }

    /** 写入数据库和 JSON 的取值。 */
    public String code() {
        return code;
    }

    /** 把库存或请求里的取值还原成枚举。空串视为未填。无法识别时抛出，避免悄悄落成 null。 */
    @JsonCreator
    public static OrderStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (OrderStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException(code);
    }
}
