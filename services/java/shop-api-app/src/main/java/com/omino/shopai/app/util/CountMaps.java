package com.omino.shopai.app.util;

import java.math.BigDecimal;

/**
 * 销量与评价数。平台没给或给了无法解析的文案时保持 null，数字 0 保留。
 */
public final class CountMaps {

    private CountMaps() {
    }

    /** 销量。无法整除成整数时视为未提供。 */
    public static Long sales(Object raw) {
        BigDecimal number = number(raw);
        if (number == null) {
            return null;
        }
        try {
            return number.longValueExact();
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    /** 评价数。超出 int 范围时视为未提供。 */
    public static Integer reviews(Object raw) {
        BigDecimal number = number(raw);
        if (number == null) {
            return null;
        }
        try {
            return number.intValueExact();
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private static BigDecimal number(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        if (raw instanceof Number number) {
            if (raw instanceof Double || raw instanceof Float) {
                double value = number.doubleValue();
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    return null;
                }
            }
            return new BigDecimal(number.toString());
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
