package com.omino.shopai.app.adapter.pay;
/**
 * 验签通过后的支付通知。
 */

public interface PayNotice {

    boolean accepted(String body);
}
