package cn.orangenode.forge.app.service.impl;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.orangenode.forge.app.entity.AppSettingsEntity;
import cn.orangenode.forge.app.mapper.AppSettingsMapper;
import cn.orangenode.forge.app.request.AppFeatureRequest;
import cn.orangenode.forge.app.request.AppHotSearchRequest;
import cn.orangenode.forge.app.response.AppFeatureResponse;
import cn.orangenode.forge.app.response.AppHotSearchResponse;
import cn.orangenode.forge.app.service.AppSettingsService;
import cn.orangenode.forge.app.support.AppValues;
import cn.orangenode.forge.framework.security.AuthenticatedAdmin;

import lombok.RequiredArgsConstructor;

/**
 * 功能开关与热搜设置实现，写入单行设置。
 */
@Service
@RequiredArgsConstructor
public class AppSettingsServiceImpl implements AppSettingsService {
    private final AppSettingsMapper mapper;
    private final AppSettingsStore store;

    /**
     * 读取功能开关。
     */
    @Override
    public AppFeatureResponse features() {
        AppSettingsEntity settings = store.require();
        return new AppFeatureResponse(Boolean.TRUE.equals(settings.getVipRequired()),
                Boolean.TRUE.equals(settings.getRegistrationOpen()), settings.getMaxDevices(),
                settings.getServiceText(), settings.getNoticeText(), AppValues.time(settings.getUpdatedAt()));
    }

    /**
     * 修改功能开关。
     */
    @Override
    @Transactional
    public AppFeatureResponse updateFeatures(AppFeatureRequest request) {
        store.require();
        mapper.update(null, Wrappers.<AppSettingsEntity>lambdaUpdate()
                .set(AppSettingsEntity::getVipRequired, request.vipRequired())
                .set(AppSettingsEntity::getRegistrationOpen, request.registrationOpen())
                .set(AppSettingsEntity::getMaxDevices, request.maxDevices())
                .set(AppSettingsEntity::getServiceText, AppValues.clean(request.serviceText()))
                .set(AppSettingsEntity::getNoticeText, AppValues.clean(request.noticeText()))
                .set(AppSettingsEntity::getUpdatedBy, currentAdminId())
                .set(AppSettingsEntity::getUpdatedAt, AppValues.now())
                .eq(AppSettingsEntity::getId, AppSettingsStore.SETTINGS_ID));
        return features();
    }

    /**
     * 读取热搜设置。
     */
    @Override
    public AppHotSearchResponse hotSearch() {
        AppSettingsEntity settings = store.require();
        return new AppHotSearchResponse(store.words(settings.getHotWords()), store.words(settings.getBlockedWords()));
    }

    /**
     * 修改热搜设置，词表去空白去重后保存。
     */
    @Override
    @Transactional
    public AppHotSearchResponse updateHotSearch(AppHotSearchRequest request) {
        store.require();
        mapper.update(null, Wrappers.<AppSettingsEntity>lambdaUpdate()
                .set(AppSettingsEntity::getHotWords, store.json(store.normalize(request.hotWords())))
                .set(AppSettingsEntity::getBlockedWords, store.json(store.normalize(request.blockedWords())))
                .set(AppSettingsEntity::getUpdatedBy, currentAdminId())
                .set(AppSettingsEntity::getUpdatedAt, AppValues.now())
                .eq(AppSettingsEntity::getId, AppSettingsStore.SETTINGS_ID));
        return hotSearch();
    }

    /**
     * 从安全上下文读取当前管理员 ID。
     */
    private Long currentAdminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedAdmin admin
                ? admin.id() : null;
    }
}
