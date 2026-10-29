package com.omino.shopai.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 二值开关。1 开，0 关。用于推送、模型启用、强更和已安装。 库存的是数字 code，不是枚举名。 */
public enum OnOff {
    OFF(0),
    ON(1);

    @EnumValue
    @JsonValue
    private final int code;

    OnOff(int code) {
        this.code = code;
    }

    /** 写入数据库和 JSON 的取值。 */
    public int code() {
        return code;
    }

    /** 把 0/1 或请求里的数字还原成枚举。无法识别时抛出。 */
    @JsonCreator
    public static OnOff fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OnOff value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException(String.valueOf(code));
    }
}
