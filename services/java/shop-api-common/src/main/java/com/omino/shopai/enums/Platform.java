package com.omino.shopai.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 电商平台。只有淘宝、京东、拼多多、唯品会。 库存的是 code，不是枚举名。 */
public enum Platform {
    TAOBAO("taobao"),
    JD("jd"),
    PDD("pdd"),
    VIP("vip");

    @EnumValue
    @JsonValue
    private final String code;

    Platform(String code) {
        this.code = code;
    }

    /**
     * 写入数据库和 JSON 的取值。
     *
     * @return 平台码
     */
    public String code() {
        return code;
    }

    /**
     * 出站时该读哪一条授权。拼多多的 pid 用该用户的京东授权，其它平台用自己的授权。
     *
     * @return 要查询的授权平台
     */
    public Platform relationPlatform() {
        return this == PDD ? JD : this;
    }

    /**
     * 把库存或请求里的取值还原成枚举。空串视为未填。无法识别时抛出，避免悄悄落成 null。
     *
     * @param code 平台码
     * @return 对应枚举；空串为 null
     */
    @JsonCreator
    public static Platform fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (Platform value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException(code);
    }
}
