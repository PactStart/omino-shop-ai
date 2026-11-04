package com.omino.shopai.admin.web.dto;

/**
 * 设置一个平台的两条同步水位。空或空串表示清掉该列。
 *
 * @param lastSyncCreateTime 下单水位，可空
 * @param lastSyncUpdateTime 更新水位，可空
 */
public record OrderSyncCursorDto(String lastSyncCreateTime, String lastSyncUpdateTime) {
}
