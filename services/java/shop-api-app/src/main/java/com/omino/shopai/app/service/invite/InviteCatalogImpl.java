package com.omino.shopai.app.service.invite;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omino.shopai.entity.AppUser;
import com.omino.shopai.entity.InvitePosterTemplate;
import com.omino.shopai.mapper.AppUserMapper;
import com.omino.shopai.mapper.InvitePosterTemplateMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 持久化边界。事务和状态判断由调用方负责。
 */

@Repository
public class InviteCatalogImpl implements IInviteCatalog {

    private final InvitePosterTemplateMapper posters;
    private final AppUserMapper users;
/**
 * 邀请码和海报模板的 MyBatis 访问。
 */

    public InviteCatalogImpl(InvitePosterTemplateMapper posters, AppUserMapper users) {
        this.posters = posters;
        this.users = users;
    }

    @Override
    public List<InvitePosterTemplate> posters() {
        return posters.selectList(new LambdaQueryWrapper<InvitePosterTemplate>().orderByAsc(InvitePosterTemplate::getSort));
    }

    @Override
    public Optional<AppUser> findById(String id) {
        return Optional.ofNullable(users.selectById(id));
    }

    @Override
    public Optional<AppUser> findByInviteCode(String code) {
        return Optional.ofNullable(users.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getInviteCode, code)));
    }
}
