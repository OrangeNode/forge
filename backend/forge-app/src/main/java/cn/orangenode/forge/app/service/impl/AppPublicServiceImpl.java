package cn.orangenode.forge.app.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.orangenode.forge.app.converter.AppConverter;
import cn.orangenode.forge.app.entity.AppBannerEntity;
import cn.orangenode.forge.app.entity.AppSectionEntity;
import cn.orangenode.forge.app.entity.AppSettingsEntity;
import cn.orangenode.forge.app.entity.AppSplashEntity;
import cn.orangenode.forge.app.mapper.AppBannerMapper;
import cn.orangenode.forge.app.mapper.AppSectionMapper;
import cn.orangenode.forge.app.mapper.AppSplashMapper;
import cn.orangenode.forge.app.response.AppHomeBannerResponse;
import cn.orangenode.forge.app.response.AppHomeResponse;
import cn.orangenode.forge.app.response.AppHomeSectionResponse;
import cn.orangenode.forge.app.response.AppHotWordsResponse;
import cn.orangenode.forge.app.response.AppLaunchResponse;
import cn.orangenode.forge.app.response.AppLaunchSplashResponse;
import cn.orangenode.forge.app.response.AppVideoCardResponse;
import cn.orangenode.forge.app.service.AppPublicService;
import cn.orangenode.forge.app.support.AppValues;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.catalog.VideoCatalogPort;
import cn.orangenode.forge.framework.storage.StoredFileContent;
import cn.orangenode.forge.framework.storage.StoredFileReader;

import lombok.RequiredArgsConstructor;

/**
 * App 端读取实现：只输出启用且处于生效时间内的内容，影片卡片只来自已上架且分类可见的影片。
 */
@Service
@RequiredArgsConstructor
public class AppPublicServiceImpl implements AppPublicService {
    private static final String SOURCE_CATEGORY = "CATEGORY";
    private final AppSplashMapper splashes;
    private final AppBannerMapper banners;
    private final AppSectionMapper sections;
    private final AppSettingsStore settings;
    private final VideoCatalogPort catalog;
    private final StoredFileReader files;
    private final AppConverter converter;

    /**
     * 读取启动配置，开屏取优先顺序最靠前的生效项。
     */
    @Override
    public AppLaunchResponse launch() {
        LocalDateTime now = AppValues.now();
        AppLaunchSplashResponse splash = splashes.selectList(Wrappers.<AppSplashEntity>lambdaQuery()
                .eq(AppSplashEntity::getEnabled, true).orderByAsc(AppSplashEntity::getSortNo)
                .orderByDesc(AppSplashEntity::getId)).stream()
                .filter(e -> AppValues.active(e.getEnabled(), e.getStartAt(), e.getEndAt(), now)).findFirst()
                .map(e -> new AppLaunchSplashResponse(AppValues.imageUrl(e.getImageFileId()), e.getDurationSeconds(),
                        Boolean.TRUE.equals(e.getSkippable()))).orElse(null);
        AppSettingsEntity s = settings.require();
        return new AppLaunchResponse(splash, Boolean.TRUE.equals(s.getVipRequired()),
                Boolean.TRUE.equals(s.getRegistrationOpen()), s.getServiceText(), s.getNoticeText());
    }

    /**
     * 读取首页轮播与推荐行，没有可用影片的推荐行不返回。
     */
    @Override
    public AppHomeResponse home() {
        LocalDateTime now = AppValues.now();
        List<AppHomeBannerResponse> bannerList = banners.selectList(Wrappers.<AppBannerEntity>lambdaQuery()
                .eq(AppBannerEntity::getEnabled, true).orderByAsc(AppBannerEntity::getSortNo)
                .orderByDesc(AppBannerEntity::getId)).stream()
                .filter(e -> AppValues.active(e.getEnabled(), e.getStartAt(), e.getEndAt(), now))
                .map(e -> new AppHomeBannerResponse(AppValues.textId(e.getId()), e.getTitle(),
                        AppValues.imageUrl(e.getImageFileId()), e.getTargetType(), AppValues.textId(e.getTargetId())))
                .toList();
        List<AppHomeSectionResponse> sectionList = sections.selectList(Wrappers.<AppSectionEntity>lambdaQuery()
                .eq(AppSectionEntity::getEnabled, true).orderByAsc(AppSectionEntity::getSortNo)
                .orderByAsc(AppSectionEntity::getId)).stream()
                .map(this::section).filter(section -> !section.videos().isEmpty()).toList();
        return new AppHomeResponse(bannerList, sectionList);
    }

    /**
     * 读取热搜词并排除包含屏蔽词的项，比较不区分大小写。
     */
    @Override
    public AppHotWordsResponse hotWords() {
        AppSettingsEntity s = settings.require();
        List<String> blocked = settings.words(s.getBlockedWords()).stream()
                .map(word -> word.toLowerCase(Locale.ROOT)).toList();
        return new AppHotWordsResponse(settings.words(s.getHotWords()).stream()
                .filter(word -> blocked.stream().noneMatch(block -> word.toLowerCase(Locale.ROOT).contains(block)))
                .toList());
    }

    /**
     * 只开放开屏或轮播引用的图片，避免借此读取任意上传文件。
     */
    @Override
    public StoredFileContent image(Long fileId) {
        boolean referenced = splashes.selectCount(Wrappers.<AppSplashEntity>lambdaQuery()
                .eq(AppSplashEntity::getImageFileId, fileId)) > 0
                || banners.selectCount(Wrappers.<AppBannerEntity>lambdaQuery()
                .eq(AppBannerEntity::getImageFileId, fileId)) > 0;
        if (!referenced) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        }
        StoredFileContent content = files.open(fileId);
        if (content.contentType() == null || !content.contentType().startsWith("image/")) {
            try {
                content.stream().close();
            } catch (java.io.IOException ignored) {
                // 关闭失败不影响拒绝结果
            }
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        }
        return content;
    }

    /**
     * 组装单个推荐行。
     */
    private AppHomeSectionResponse section(AppSectionEntity e) {
        List<AppVideoCardResponse> videos = (SOURCE_CATEGORY.equals(e.getSourceType())
                ? catalog.latestByCategory(e.getCategoryId(), e.getItemLimit())
                : catalog.findPublic(AppContentServiceImpl.videoIds(e.getVideoIds())).stream()
                        .limit(e.getItemLimit()).toList())
                .stream().map(converter::card).toList();
        return new AppHomeSectionResponse(AppValues.textId(e.getId()), e.getTitle(), e.getSourceType(),
                AppValues.textId(e.getCategoryId()), videos);
    }
}
