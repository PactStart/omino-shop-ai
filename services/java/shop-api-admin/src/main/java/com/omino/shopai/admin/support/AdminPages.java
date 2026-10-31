package com.omino.shopai.admin.support;

import com.omino.shopai.dto.PageDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 后台列表的页码和金额文本。全仓库分页形状仍只有 PageDto。
 */
public final class AdminPages {

    private AdminPages() {
    }

    /**
     * 把请求里的页码收成从 1 起的整数。
     *
     * @param page 调用方传入的页码，可空
     * @return 至少为 1 的页码
     */
    public static int page(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    /**
     * 把请求里的页大小收成 1 到 100。
     *
     * @param size 调用方传入的页大小，可空
     * @return 默认 20，最大 100
     */
    public static int size(Integer size) {
        if (size == null || size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }

    /**
     * 在内存里切出一页。空列表的总数是 0。
     *
     * @param all 已按展示顺序排好的全部行
     * @param page 页码，从 1 起；空则当作 1
     * @param size 页大小；空则当作 20
     * @param <T> 行的类型
     * @return 当前页和总数
     */
    public static <T> PageDto<T> of(List<T> all, Integer page, Integer size) {
        int pageNo = page(page);
        int pageSize = size(size);
        int from = (pageNo - 1) * pageSize;
        if (from >= all.size()) {
            return new PageDto<>(List.of(), pageNo, pageSize, all.size());
        }
        int to = Math.min(from + pageSize, all.size());
        return new PageDto<>(List.copyOf(all.subList(from, to)), pageNo, pageSize, all.size());
    }

    /**
     * 金额写成两位小数字符串。没有金额时保持空，不写成 0。
     *
     * @param value 金额，可空
     * @return 两位小数；空输入返回 null
     */
    public static String money(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
