package com.omino.shopai.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 账号状态。1 正常，0 禁用。 库存的是数字 code，不是枚举名。 */
public enum UserStatus {
    DISABLED(0),
    ACTIVE(1);

    @EnumValue
    @JsonValue
    private final int code;

    UserStatus(int code) {
        this.code = code;
    }

    /** 写入数据库和 JSON 的取值。 */
    public int code() {
        return code;
    }

    /** 把 0/1 或请求里的数字还原成枚举。无法识别时抛出。 */
    @JsonCreator
    public static UserStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException(String.valueOf(code));
    }
}
