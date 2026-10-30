package com.omino.shopai.support;

import java.security.SecureRandom;
/**
 * 主键和邀请码。不在业务里散落随机实现。
 */

public final class Ids {

    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final String INVITE = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }
/**
 * 生成 26 位主键。不依赖数据库自增。
 */

    public static String ulid() {
        char[] out = new char[26];
        long time = System.currentTimeMillis();
        for (int i = 9; i >= 0; i--) {
            out[i] = CROCKFORD[(int) (time & 31)];
            time >>>= 5;
        }
        byte[] entropy = new byte[10];
        RANDOM.nextBytes(entropy);
        int buffer = 0;
        int bits = 0;
        int index = 10;
        for (byte value : entropy) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                out[index++] = CROCKFORD[(buffer >> bits) & 31];
            }
        }
        return new String(out);
    }
/**
 * 生成邀请码。字符集避开容易看混的符号。
 */

    public static String inviteCode() {
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            code.append(INVITE.charAt(RANDOM.nextInt(INVITE.length())));
        }
        return code.toString();
    }
}
