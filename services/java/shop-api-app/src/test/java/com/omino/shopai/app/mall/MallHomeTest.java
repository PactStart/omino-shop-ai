package com.omino.shopai.app.mall;

import com.omino.shopai.app.dto.MallHomeDto;
import com.omino.shopai.app.service.mall.MallServiceImpl;
import com.omino.shopai.entity.MallCategory;
import com.omino.shopai.entity.MallEntry;
import com.omino.shopai.entity.MallZone;
import com.omino.shopai.enums.MallActionType;
import com.omino.shopai.enums.MallZoneCode;
import com.omino.shopai.enums.PublishStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MallHomeTest {

    @Test
    void keepsVisibleBannerAndGroupedIcons() {
        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.parse("2026-11-07T00:00:00Z"), ZoneOffset.UTC);
        MallZone banner = zone(MallZoneCode.BANNER, null, 4000);
        MallZone nav = zone(MallZoneCode.NAV, 4, null);
        MallCategory beauty = category("cat-beauty", "美妆", PublishStatus.PUBLISHED);
        MallCategory draft = category("cat-draft", "草稿", PublishStatus.DRAFT);
        MemoryMall mall = new MemoryMall(
                List.of(banner, nav),
                List.of(beauty, draft),
                List.of(
                        entry("b1", MallZoneCode.BANNER, null, PublishStatus.PUBLISHED, "[]", 0),
                        entry("b-draft", MallZoneCode.BANNER, null, PublishStatus.DRAFT, "[]", 1),
                        entry("b-ios", MallZoneCode.BANNER, null, PublishStatus.PUBLISHED, "[\"ios\"]", 2),
                        entry("n1", MallZoneCode.NAV, "cat-beauty", PublishStatus.PUBLISHED, "[]", 0),
                        entry("n-draft-cat", MallZoneCode.NAV, "cat-draft", PublishStatus.PUBLISHED, "[]", 0)));

        MallHomeDto home = mall.home("h5", "web");

        assertEquals(4000, home.banner().intervalMs());
        assertEquals(1, home.banner().items().size());
        assertEquals("b1", home.banner().items().get(0).id());
        assertEquals(4, home.nav().columns());
        assertEquals(1, home.nav().categories().size());
        assertEquals("美妆", home.nav().categories().get(0).name());
        assertEquals("n1", home.nav().categories().get(0).items().get(0).id());
        assertTrue(home.banner().items().get(0).imageUrl().startsWith("https://"));
        assertEquals(now.getOffset(), ZoneOffset.UTC);
    }

    private static MallZone zone(MallZoneCode code, Integer columns, Integer interval) {
        MallZone row = new MallZone();
        row.setCode(code);
        row.setColumns(columns);
        row.setIntervalMs(interval);
        return row;
    }

    private static MallCategory category(String id, String name, PublishStatus status) {
        MallCategory row = new MallCategory();
        row.setId(id);
        row.setName(name);
        row.setSort(0);
        row.setStatus(status);
        return row;
    }

    private static MallEntry entry(
            String id, MallZoneCode zone, String categoryId, PublishStatus status, String clients, int sort) {
        MallEntry row = new MallEntry();
        row.setId(id);
        row.setZone(zone);
        row.setCategoryId(categoryId);
        row.setTitle("名称");
        row.setImageObjectKey("https://img.example/" + id + ".png");
        row.setSort(sort);
        row.setStatus(status);
        row.setClientTypes(clients);
        row.setChannels("[]");
        row.setActionType(MallActionType.NONE);
        row.setActionPayload("{}");
        return row;
    }

    private static final class MemoryMall extends MallServiceImpl {
        private final List<MallZone> zones;
        private final List<MallCategory> categories;
        private final List<MallEntry> entries;

        private MemoryMall(List<MallZone> zones, List<MallCategory> categories, List<MallEntry> entries) {
            super(null, null, null, Clock.fixed(Instant.parse("2026-11-07T00:00:00Z"), ZoneOffset.UTC));
            this.zones = zones;
            this.categories = categories;
            this.entries = entries;
        }

        @Override
        protected List<MallZone> loadZones() {
            return zones;
        }

        @Override
        protected List<MallCategory> loadCategories() {
            return categories.stream().filter(row -> row.getStatus() == PublishStatus.PUBLISHED).toList();
        }

        @Override
        protected List<MallEntry> loadEntries() {
            return entries;
        }
    }
}
