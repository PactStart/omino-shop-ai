package com.omino.shopai.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 工单流水种类。只追加，不改历史。 库存的是 code，不是枚举名。 */
public enum TicketLogKind {
    CREATED("created"),
    STATUS_CHANGE("status_change"),
    PRIORITY_CHANGE("priority_change"),
    EVIDENCE("evidence"),
    REPLY("reply"),
    ASSIGNEE_CHANGE("assignee_change");

    @EnumValue
    @JsonValue
    private final String code;

    TicketLogKind(String code) {
        this.code = code;
    }

    /** 写入数据库和 JSON 的取值。 */
    public String code() {
        return code;
    }

    /** 把库存或请求里的取值还原成枚举。空串视为未填。无法识别时抛出，避免悄悄落成 null。 */
    @JsonCreator
    public static TicketLogKind fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (TicketLogKind value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException(code);
    }
}
