package com.omino.shopai.admin.service.mall;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.admin.web.dto.MallCategoryRequestDto;
import com.omino.shopai.admin.web.dto.MallEntryRequestDto;
import com.omino.shopai.admin.web.dto.MallZonePatchDto;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.entity.MallCategory;
import com.omino.shopai.entity.MallEntry;
import com.omino.shopai.entity.MallZone;
import com.omino.shopai.enums.MallActionType;
import com.omino.shopai.enums.MallZoneCode;
import com.omino.shopai.enums.PublishStatus;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.MallCategoryMapper;
import com.omino.shopai.mapper.MallEntryMapper;
import com.omino.shopai.mapper.MallZoneMapper;
import com.omino.shopai.support.Ids;
import com.omino.shopai.support.MallActions;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 运营维护商城版位、分类和条目。不物理删除。
 */
@Service
public class AdminMallService {

    private final MallZoneMapper zones;
    private final MallCategoryMapper categories;
    private final MallEntryMapper entries;
    private final ObjectMapper json;

    /**
     * 注入三张布局表和 JSON 工具。
     *
     * @param zones 版位表
     * @param categories 分类表
     * @param entries 条目表
     * @param json 把库存的 JSON 文本读回对象
     */
    public AdminMallService(
            MallZoneMapper zones, MallCategoryMapper categories, MallEntryMapper entries, ObjectMapper json) {
        this.zones = zones;
        this.categories = categories;
        this.entries = entries;
        this.json = json;
    }

    /**
     * 列出两个版位。
     *
     * @return 版位
     */
    public List<MallZoneView> zones() {
        List<MallZone> rows = new ArrayList<>(zones.selectList(null));
        rows.sort(Comparator.comparing(row -> row.getCode() == null ? "" : row.getCode().code()));
        List<MallZoneView> views = new ArrayList<>();
        for (MallZone row : rows) {
            views.add(zoneView(row));
        }
        return views;
    }

    /**
     * 改轮播间隔或金刚区每行个数。另一个字段保持不动。
     *
     * @param code 版位码
     * @param patch 要改的参数
     * @return 更新后的版位
     */
    public MallZoneView updateZone(String code, MallZonePatchDto patch) {
        MallZoneCode place = MallActions.zoneOf(code);
        MallZone row = zones.selectById(place);
        if (row == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "版位不存在");
        }
        if (place == MallZoneCode.BANNER) {
            Integer interval = patch == null ? null : patch.intervalMs();
            if (interval == null || interval < 0 || interval > 60000) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "轮播间隔不正确");
            }
            row.setIntervalMs(interval);
        } else {
            Integer columns = patch == null ? null : patch.columns();
            if (columns == null || columns < 4 || columns > 6) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "每行个数须为 4 到 6");
            }
            row.setColumns(columns);
        }
        row.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        zones.updateById(row);
        return zoneView(row);
    }

    /**
     * 按排序列出全部分类。
     *
     * @return 分类
     */
    public List<MallCategoryView> categories() {
        List<MallCategoryView> views = new ArrayList<>();
        for (MallCategory row : categories.selectList(new LambdaQueryWrapper<MallCategory>().orderByAsc(MallCategory::getSort))) {
            views.add(categoryView(row));
        }
        return views;
    }

    /**
     * 新建一个分类。
     *
     * @param request 名称、排序和状态
     * @return 新建的分类
     */
    public MallCategoryView createCategory(MallCategoryRequestDto request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        MallCategory row = new MallCategory();
        row.setId(Ids.ulid());
        fillCategory(row, request);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        categories.insert(row);
        return categoryView(row);
    }

    /**
     * 修改一个分类。不存在则拒绝。
     *
     * @param id 分类主键
     * @param request 名称、排序和状态
     * @return 更新后的分类
     */
    public MallCategoryView updateCategory(String id, MallCategoryRequestDto request) {
        MallCategory row = categories.selectById(id);
        if (row == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "分类不存在");
        }
        fillCategory(row, request);
        row.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        categories.updateById(row);
        return categoryView(row);
    }

    /**
     * 列出条目。可以只看一个版位。
     *
     * @param zone 版位码，可空
     * @return 条目，点击参数已还原成对象
     */
    public List<MallEntryView> entries(String zone) {
        MallZoneCode place = zone == null || zone.isBlank() ? null : MallActions.zoneOf(zone);
        List<MallEntry> rows = entries.selectList(new LambdaQueryWrapper<MallEntry>()
                .eq(place != null, MallEntry::getZone, place)
                .orderByAsc(MallEntry::getSort));
        List<MallEntryView> views = new ArrayList<>();
        for (MallEntry row : rows) {
            views.add(view(row));
        }
        return views;
    }

    /**
     * 新建一条条目。分类必须存在，点击参数先校验。
     *
     * @param request 条目内容
     * @return 新建后的条目
     */
    public MallEntryView createEntry(MallEntryRequestDto request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        MallEntry row = new MallEntry();
        row.setId(Ids.ulid());
        fillEntry(row, request);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        entries.insertJson(row);
        return view(row);
    }

    /**
     * 修改一条条目。不存在则拒绝。
     *
     * @param id 条目主键
     * @param request 条目内容
     * @return 更新后的条目
     */
    public MallEntryView updateEntry(String id, MallEntryRequestDto request) {
        MallEntry row = entries.selectById(id);
        if (row == null) {
            throw new ApiBusinessException(ErrorCodes.NOT_FOUND, "条目不存在");
        }
        fillEntry(row, request);
        row.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entries.updateJson(row);
        return view(row);
    }

    /**
     * 把请求写进分类。名称和状态必填。
     *
     * @param row 分类
     * @param request 请求
     */
    private void fillCategory(MallCategory row, MallCategoryRequestDto request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "分类名不能为空");
        }
        if (request.name().trim().length() > 32) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "分类名过长");
        }
        row.setName(request.name().trim());
        row.setSort(request.sort() == null ? 0 : request.sort());
        row.setStatus(status(request.status()));
    }

    /**
     * 把请求写进条目，并确认分类还在。
     *
     * @param row 条目
     * @param request 请求
     */
    private void fillEntry(MallEntry row, MallEntryRequestDto request) {
        if (request == null) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "条目不能为空");
        }
        if (request.startsAt() != null && request.endsAt() != null && !request.endsAt().isAfter(request.startsAt())) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "结束时间必须晚于开始时间");
        }
        MallActions.checkEntry(
                request.zone(),
                request.categoryId(),
                request.title(),
                request.imageObjectKey(),
                request.actionType(),
                request.action());
        MallZoneCode place = MallActions.zoneOf(request.zone());
        if (place == MallZoneCode.NAV && categories.selectById(request.categoryId()) == null) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "分类不存在");
        }
        row.setZone(place);
        row.setCategoryId(place == MallZoneCode.NAV ? request.categoryId() : null);
        row.setTitle(request.title() == null ? null : request.title().trim());
        row.setImageObjectKey(request.imageObjectKey().trim());
        row.setSort(request.sort() == null ? 0 : request.sort());
        row.setStatus(status(request.status()));
        row.setStartsAt(request.startsAt());
        row.setEndsAt(request.endsAt());
        row.setClientTypes(MallActions.clients(request.clientTypes()));
        row.setChannels(MallActions.channels(request.channels()));
        row.setActionType(MallActionType.fromCode(request.actionType()));
        JsonNode action = request.action() == null || request.action().isNull() ? json.createObjectNode() : request.action();
        row.setActionPayload(MallActions.write(action));
    }

    /**
     * 把状态码还原成枚举。空或不认识都拒绝。
     *
     * @param code 状态码
     * @return 状态
     */
    private static PublishStatus status(String code) {
        try {
            PublishStatus value = PublishStatus.fromCode(code);
            if (value == null) {
                throw new IllegalArgumentException("blank");
            }
            return value;
        } catch (IllegalArgumentException ex) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "状态不正确");
        }
    }

    /**
     * 版位给后台时只留码和展示参数。
     *
     * @param row 库存版位
     * @return 后台看到的版位
     */
    private MallZoneView zoneView(MallZone row) {
        return new MallZoneView(
                row.getCode() == null ? null : row.getCode().code(),
                row.getColumns(),
                row.getIntervalMs(),
                row.getUpdatedAt());
    }

    /**
     * 分类给后台时带上状态码。
     *
     * @param row 库存分类
     * @return 后台看到的分类
     */
    private MallCategoryView categoryView(MallCategory row) {
        return new MallCategoryView(
                row.getId(),
                row.getName(),
                row.getSort(),
                row.getStatus() == null ? null : row.getStatus().code(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    /**
     * 条目给后台时，点击参数恢复成对象，端和渠道恢复成列表。
     *
     * @param row 库存条目
     * @return 后台看到的条目
     */
    private MallEntryView view(MallEntry row) {
        return new MallEntryView(
                row.getId(),
                row.getZone() == null ? null : row.getZone().code(),
                row.getCategoryId(),
                row.getTitle(),
                row.getImageObjectKey(),
                row.getSort(),
                row.getStatus() == null ? null : row.getStatus().code(),
                row.getStartsAt(),
                row.getEndsAt(),
                readList(row.getClientTypes()),
                readList(row.getChannels()),
                row.getActionType() == null ? null : row.getActionType().code(),
                readNode(row.getActionPayload()),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    /**
     * 把 JSON 数组文本读成字符串列表。坏数据当成空列表。
     *
     * @param raw JSON 数组文本
     * @return 字符串列表
     */
    private List<String> readList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return json.readerForListOf(String.class).readValue(raw);
        } catch (Exception ex) {
            return List.of();
        }
    }

    /**
     * 把 JSON 对象文本读成节点。坏数据当成空对象。
     *
     * @param raw JSON 对象文本
     * @return 对象节点
     */
    private JsonNode readNode(String raw) {
        JsonNode node = MallActions.readAction(raw);
        return node == null ? json.createObjectNode() : node;
    }

    /**
     * 后台看到的一个版位。
     *
     * @param code 版位码
     * @param columns 每行个数
     * @param intervalMs 轮播间隔
     * @param updatedAt 更新时间
     */
    public record MallZoneView(String code, Integer columns, Integer intervalMs, OffsetDateTime updatedAt) {
    }

    /**
     * 后台看到的一个分类。
     *
     * @param id 主键
     * @param name 名称
     * @param sort 排序
     * @param status 状态码
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public record MallCategoryView(
            String id, String name, Integer sort, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    /**
     * 后台看到的一条商城条目。点击参数是对象，不是库存里的文本。
     *
     * @param id 主键
     * @param zone 版位码
     * @param categoryId 分类主键
     * @param title 标题
     * @param imageObjectKey 图片地址或对象键
     * @param sort 排序
     * @param status 状态码
     * @param startsAt 生效开始
     * @param endsAt 生效结束
     * @param clientTypes 端
     * @param channels 渠道
     * @param actionType 点击类型
     * @param action 点击参数
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public record MallEntryView(
            String id,
            String zone,
            String categoryId,
            String title,
            String imageObjectKey,
            Integer sort,
            String status,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            List<String> clientTypes,
            List<String> channels,
            String actionType,
            JsonNode action,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }
}
