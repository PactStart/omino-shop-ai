package com.omino.shopai.app.service.mall;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omino.shopai.app.dto.MallBannerDto;
import com.omino.shopai.app.dto.MallCategoryDto;
import com.omino.shopai.app.dto.MallHomeDto;
import com.omino.shopai.app.dto.MallItemDto;
import com.omino.shopai.app.dto.MallNavDto;
import com.omino.shopai.entity.MallCategory;
import com.omino.shopai.entity.MallEntry;
import com.omino.shopai.entity.MallZone;
import com.omino.shopai.enums.MallZoneCode;
import com.omino.shopai.enums.PublishStatus;
import com.omino.shopai.mapper.MallCategoryMapper;
import com.omino.shopai.mapper.MallEntryMapper;
import com.omino.shopai.mapper.MallZoneMapper;
import com.omino.shopai.support.MallActions;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 从三张布局表筛出当前端可见的商城首页。
 */
@Service
public class MallServiceImpl implements IMallService {

    private final MallZoneMapper zones;
    private final MallCategoryMapper categories;
    private final MallEntryMapper entries;
    private final Clock clock;

    /**
     * 注入版位、分类、条目和时钟。
     *
     * @param zones 版位表
     * @param categories 分类表
     * @param entries 条目表
     * @param clock 判断生效窗
     */
    public MallServiceImpl(
            MallZoneMapper zones, MallCategoryMapper categories, MallEntryMapper entries, Clock clock) {
        this.zones = zones;
        this.categories = categories;
        this.entries = entries;
        this.clock = clock;
    }

    /**
     * 读出当前端能看的轮播和金刚区。坏的点击参数会被丢掉，不让整页失败。
     *
     * @param clientType 客户端类型
     * @param channel 渠道
     * @return 两块布局
     */
    @Override
    public MallHomeDto home(String clientType, String channel) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<MallEntry> visible = loadEntries().stream()
                .filter(row -> row.getStatus() == PublishStatus.PUBLISHED)
                .filter(row -> visibleAt(row, now))
                .filter(row -> MallActions.allows(row.getClientTypes(), clientType))
                .filter(row -> MallActions.allows(row.getChannels(), channel))
                .sorted(Comparator.comparingInt(row -> row.getSort() == null ? 0 : row.getSort()))
                .toList();
        MallZone bannerZone = zone(MallZoneCode.BANNER);
        MallZone navZone = zone(MallZoneCode.NAV);
        int interval = bannerZone == null || bannerZone.getIntervalMs() == null ? 3000 : bannerZone.getIntervalMs();
        int columns = navZone == null || navZone.getColumns() == null ? 5 : navZone.getColumns();
        return new MallHomeDto(
                new MallBannerDto(interval, items(visible, MallZoneCode.BANNER, null)),
                new MallNavDto(columns, groups(visible)));
    }

    /**
     * 读出全部版位。
     *
     * @return 版位
     */
    protected List<MallZone> loadZones() {
        return zones.selectList(null);
    }

    /**
     * 读出已发布的分类，按排序。
     *
     * @return 分类
     */
    protected List<MallCategory> loadCategories() {
        return categories.selectList(new LambdaQueryWrapper<MallCategory>()
                .eq(MallCategory::getStatus, PublishStatus.PUBLISHED)
                .orderByAsc(MallCategory::getSort));
    }

    /**
     * 读出全部条目。是否可见由调用方判断。
     *
     * @return 条目
     */
    protected List<MallEntry> loadEntries() {
        return entries.selectList(null);
    }

    /**
     * 按版位码找配置。没有时为空。
     *
     * @param code 版位
     * @return 版位；没有则空
     */
    private MallZone zone(MallZoneCode code) {
        for (MallZone row : loadZones()) {
            if (row.getCode() == code) {
                return row;
            }
        }
        return null;
    }

    /**
     * 把仍有图标的分类收成金刚区。
     *
     * @param visible 已经筛过的条目
     * @return 分类列表
     */
    private List<MallCategoryDto> groups(List<MallEntry> visible) {
        List<MallCategoryDto> groups = new ArrayList<>();
        for (MallCategory category : loadCategories()) {
            List<MallItemDto> icons = items(visible, MallZoneCode.NAV, category.getId());
            if (!icons.isEmpty()) {
                groups.add(new MallCategoryDto(category.getId(), category.getName(), icons));
            }
        }
        return groups;
    }

    /**
     * 取出某个版位、某个分类下能展示的条目。
     *
     * @param visible 已经筛过的条目
     * @param zone 版位
     * @param categoryId 分类主键；轮播传空
     * @return 可展示的条目
     */
    private List<MallItemDto> items(List<MallEntry> visible, MallZoneCode zone, String categoryId) {
        List<MallItemDto> found = new ArrayList<>();
        for (MallEntry row : visible) {
            if (row.getZone() != zone) {
                continue;
            }
            if (categoryId != null && !categoryId.equals(row.getCategoryId())) {
                continue;
            }
            ObjectNode action = MallActions.readAction(row.getActionPayload());
            if (action == null || row.getActionType() == null) {
                continue;
            }
            found.add(new MallItemDto(
                    row.getId(),
                    row.getTitle(),
                    imageUrl(row.getImageObjectKey()),
                    row.getActionType().code(),
                    action));
        }
        return found;
    }

    /**
     * 开始时间含、结束时间不含。两端都空则一直可见。
     *
     * @param row 条目
     * @param now 当前时刻
     * @return 落在窗内时为真
     */
    private static boolean visibleAt(MallEntry row, OffsetDateTime now) {
        if (row.getStartsAt() != null && now.isBefore(row.getStartsAt())) {
            return false;
        }
        return row.getEndsAt() == null || now.isBefore(row.getEndsAt());
    }

    /**
     * http 地址原样下发。对象键也原样下发，由端上或网关决定怎么访问。
     *
     * @param key 图片地址或对象键
     * @return 展示地址
     */
    private static String imageUrl(String key) {
        return key == null ? "" : key;
    }
}
