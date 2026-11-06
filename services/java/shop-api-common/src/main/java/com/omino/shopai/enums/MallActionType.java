package com.omino.shopai.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 商城条目的点击类型。库存的是 code，不是枚举名。 */
public enum MallActionType {
    NONE("none"),
    PAGE("page"),
    GOODS("goods"),
    SEARCH("search"),
    MINIPROGRAM("miniprogram"),
    DEEPLINK("deeplink"),
    WEBVIEW("webview");

    @EnumValue
    @JsonValue
    private final String code;

    MallActionType(String code) {
        this.code = code;
    }

    /** 写入数据库和 JSON 的取值。 */
    public String code() {
        return code;
    }

    /** 把库存或请求里的取值还原成枚举。空串视为未填。无法识别时抛出。 */
    @JsonCreator
    public static MallActionType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (MallActionType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException(code);
    }
}
