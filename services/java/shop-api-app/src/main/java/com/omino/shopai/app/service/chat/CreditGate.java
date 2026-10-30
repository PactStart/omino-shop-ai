package com.omino.shopai.app.service.chat;
/**
 * 开 run 前看积分。不够则工人不启动。
 */

public interface CreditGate {

    CreditGate ALLOW = run -> false;

    boolean block(ChatRun run);
}
