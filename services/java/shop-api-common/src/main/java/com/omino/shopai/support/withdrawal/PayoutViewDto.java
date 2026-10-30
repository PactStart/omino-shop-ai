package com.omino.shopai.support.withdrawal;
/**
 * 打款结果文案和状态码。失败时冻结额未动。
 */

public record PayoutViewDto(String message, String payoutResult, String status) {
}
