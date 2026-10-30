package com.omino.shopai.app.service.goods;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.entity.GoodsSnapshot;
import com.omino.shopai.mapper.GoodsSnapshotMapper;
import com.omino.shopai.support.Ids;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
/**
 * 持久化边界。事务和状态判断由调用方负责。
 */

@Repository
public class MybatisGoodsSnapshotStore implements GoodsSnapshotStore {

    private final GoodsSnapshotMapper snapshots;

    public MybatisGoodsSnapshotStore(GoodsSnapshotMapper snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public Optional<GoodsSnapshot> findById(String id) {
        return Optional.ofNullable(snapshots.selectById(id));
    }

    @Override
    public Optional<GoodsSnapshot> findByPlatform(String platform, String platformGoodsId) {
        return Optional.ofNullable(snapshots.selectOne(new LambdaQueryWrapper<GoodsSnapshot>()
                .eq(GoodsSnapshot::getPlatform, platform)
                .eq(GoodsSnapshot::getPlatformGoodsId, platformGoodsId)));
    }
/**
 * 保存修改。
 */

    @Override
    public GoodsSnapshot save(GoodsSnapshot snapshot) {
        if (snapshot.getId() == null || snapshot.getId().isBlank()) {
            snapshot.setId(Ids.ulid());
        }
        if (snapshot.getUpdatedAt() == null) {
            snapshot.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }
        GoodsSnapshot existing = findByPlatform(
                snapshot.getPlatform() == null ? null : snapshot.getPlatform().code(),
                snapshot.getPlatformGoodsId()).orElse(null);
        if (existing == null) {
            snapshots.insert(snapshot);
            return snapshot;
        }
        snapshot.setId(existing.getId());
        snapshots.updateById(snapshot);
        return snapshot;
    }
}
