package cn.orangenode.forge.app.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.orangenode.forge.app.config.AppProperties;
import cn.orangenode.forge.app.converter.AppConverter;
import cn.orangenode.forge.app.entity.AppBannerEntity;
import cn.orangenode.forge.app.entity.AppSectionEntity;
import cn.orangenode.forge.app.entity.AppSplashEntity;
import cn.orangenode.forge.app.mapper.AppBannerMapper;
import cn.orangenode.forge.app.mapper.AppSectionMapper;
import cn.orangenode.forge.app.mapper.AppSplashMapper;
import cn.orangenode.forge.app.request.AppBannerRequest;
import cn.orangenode.forge.app.request.AppSectionRequest;
import cn.orangenode.forge.app.request.AppSplashRequest;
import cn.orangenode.forge.app.response.AppBannerResponse;
import cn.orangenode.forge.app.response.AppPickedVideoResponse;
import cn.orangenode.forge.app.response.AppSectionResponse;
import cn.orangenode.forge.app.response.AppSplashResponse;
import cn.orangenode.forge.app.service.AppContentService;
import cn.orangenode.forge.app.support.AppValues;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.catalog.VideoCatalogPort;
import cn.orangenode.forge.framework.storage.StoredFileReader;

import lombok.RequiredArgsConstructor;

/**
 * 首页内容维护实现：图片通过文件读取端口校验存在性，影片与分类通过影片目录端口校验，不跨模块访问数据表。
 */
@Service
@RequiredArgsConstructor
public class AppContentServiceImpl implements AppContentService {
    private static final String TARGET_NONE = "NONE";
    private static final String TARGET_VIDEO = "VIDEO";
    private static final String TARGET_URL = "URL";
    private static final String SOURCE_CATEGORY = "CATEGORY";
    private final AppSplashMapper splashes;
    private final AppBannerMapper banners;
    private final AppSectionMapper sections;
    private final StoredFileReader files;
    private final VideoCatalogPort catalog;
    private final AppConverter converter;
    private final AppProperties properties;

    /**
     * 查询全部开屏。
     */
    @Override
    public List<AppSplashResponse> splashes() {
        return splashes.selectList(Wrappers.<AppSplashEntity>lambdaQuery().orderByAsc(AppSplashEntity::getSortNo)
                .orderByDesc(AppSplashEntity::getId)).stream().map(e -> converter.splash(e, splashTargetName(e)))
                .toList();
    }

    /**
     * 新增或修改开屏。
     */
    @Override
    @Transactional
    public AppSplashResponse saveSplash(Long id, AppSplashRequest r) {
        AppValues.assertWindow(r.startAt(), r.endAt());
        AppSplashEntity e = id == null ? new AppSplashEntity() : requireSplash(id);
        e.setName(r.name().strip());
        e.setImageFileId(requireImage(r.imageFileId()));
        e.setTvImageFileId(r.tvImageFileId() == null || r.tvImageFileId().isBlank() ? null
                : requireImage(r.tvImageFileId()));
        e.setDurationSeconds(r.durationSeconds());
        e.setSkippable(r.skippable());
        e.setStartAt(AppValues.utc(r.startAt()));
        e.setEndAt(AppValues.utc(r.endAt()));
        e.setEnabled(r.enabled());
        e.setSortNo(r.sortNo());
        e.setTargetType(r.targetType() == null ? TARGET_NONE : r.targetType());
        e.setTargetValue(splashTarget(e.getTargetType(), r.targetValue()));
        e.setUpdatedAt(AppValues.now());
        if (id == null) {
            e.setDeleted(0);
            e.setCreatedAt(AppValues.now());
            splashes.insert(e);
        } else {
            splashes.update(null, Wrappers.<AppSplashEntity>lambdaUpdate()
                    .set(AppSplashEntity::getName, e.getName()).set(AppSplashEntity::getImageFileId, e.getImageFileId())
                    .set(AppSplashEntity::getDurationSeconds, e.getDurationSeconds())
                    .set(AppSplashEntity::getSkippable, e.getSkippable()).set(AppSplashEntity::getStartAt, e.getStartAt())
                    .set(AppSplashEntity::getEndAt, e.getEndAt()).set(AppSplashEntity::getEnabled, e.getEnabled())
                    .set(AppSplashEntity::getSortNo, e.getSortNo()).set(AppSplashEntity::getUpdatedAt, e.getUpdatedAt())
                    .set(AppSplashEntity::getTargetType, e.getTargetType())
                    .set(AppSplashEntity::getTargetValue, e.getTargetValue())
                    .set(AppSplashEntity::getTvImageFileId, e.getTvImageFileId())
                    .eq(AppSplashEntity::getId, id));
        }
        AppSplashEntity saved = requireSplash(e.getId());
        return converter.splash(saved, splashTargetName(saved));
    }

    /**
     * 逻辑删除开屏。
     */
    @Override
    @Transactional
    public void deleteSplash(Long id) {
        requireSplash(id);
        splashes.deleteById(id);
    }

    /**
     * 查询全部轮播。
     */
    @Override
    public List<AppBannerResponse> banners() {
        return banners.selectList(Wrappers.<AppBannerEntity>lambdaQuery().orderByAsc(AppBannerEntity::getSortNo)
                .orderByDesc(AppBannerEntity::getId)).stream().map(e -> converter.banner(e, targetName(e))).toList();
    }

    /**
     * 新增或修改轮播。
     */
    @Override
    @Transactional
    public AppBannerResponse saveBanner(Long id, AppBannerRequest r) {
        AppValues.assertWindow(r.startAt(), r.endAt());
        AppBannerEntity e = id == null ? new AppBannerEntity() : requireBanner(id);
        boolean noTargetId = TARGET_NONE.equals(r.targetType()) || TARGET_URL.equals(r.targetType());
        Long targetId = noTargetId ? null : AppValues.id(r.targetId(), "跳转目标 ID");
        String targetUrl = TARGET_URL.equals(r.targetType()) ? AppValues.httpUrl(r.targetUrl(), "广告链接") : null;
        if (targetId != null && (TARGET_VIDEO.equals(r.targetType()) ? catalog.videoName(targetId)
                : catalog.categoryName(targetId)).isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "跳转的影片或分类不存在");
        }
        e.setTitle(r.title().strip());
        e.setImageFileId(requireImage(r.imageFileId()));
        e.setTvImageFileId(r.tvImageFileId() == null || r.tvImageFileId().isBlank() ? null
                : requireImage(r.tvImageFileId()));
        e.setTargetType(r.targetType());
        e.setTargetId(targetId);
        e.setTargetUrl(targetUrl);
        e.setStartAt(AppValues.utc(r.startAt()));
        e.setEndAt(AppValues.utc(r.endAt()));
        e.setEnabled(r.enabled());
        e.setSortNo(r.sortNo());
        e.setUpdatedAt(AppValues.now());
        if (id == null) {
            e.setDeleted(0);
            e.setCreatedAt(AppValues.now());
            banners.insert(e);
        } else {
            banners.update(null, Wrappers.<AppBannerEntity>lambdaUpdate()
                    .set(AppBannerEntity::getTitle, e.getTitle()).set(AppBannerEntity::getImageFileId, e.getImageFileId())
                    .set(AppBannerEntity::getTargetType, e.getTargetType()).set(AppBannerEntity::getTargetId, targetId)
                    .set(AppBannerEntity::getTargetUrl, targetUrl)
                    .set(AppBannerEntity::getTvImageFileId, e.getTvImageFileId())
                    .set(AppBannerEntity::getStartAt, e.getStartAt()).set(AppBannerEntity::getEndAt, e.getEndAt())
                    .set(AppBannerEntity::getEnabled, e.getEnabled()).set(AppBannerEntity::getSortNo, e.getSortNo())
                    .set(AppBannerEntity::getUpdatedAt, e.getUpdatedAt()).eq(AppBannerEntity::getId, id));
        }
        AppBannerEntity saved = requireBanner(e.getId());
        return converter.banner(saved, targetName(saved));
    }

    /**
     * 逻辑删除轮播。
     */
    @Override
    @Transactional
    public void deleteBanner(Long id) {
        requireBanner(id);
        banners.deleteById(id);
    }

    /**
     * 查询全部推荐位。
     */
    @Override
    public List<AppSectionResponse> sections() {
        return sections.selectList(Wrappers.<AppSectionEntity>lambdaQuery().orderByAsc(AppSectionEntity::getSortNo)
                .orderByAsc(AppSectionEntity::getId)).stream().map(this::sectionResponse).toList();
    }

    /**
     * 新增或修改推荐位。
     */
    @Override
    @Transactional
    public AppSectionResponse saveSection(Long id, AppSectionRequest r) {
        if (r.itemLimit() > properties.getSectionMaxItems()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "展示条数不能超过 " + properties.getSectionMaxItems());
        }
        AppSectionEntity e = id == null ? new AppSectionEntity() : requireSection(id);
        Long categoryId = null;
        String videoIds = null;
        if (SOURCE_CATEGORY.equals(r.sourceType())) {
            categoryId = AppValues.id(r.categoryId(), "分类 ID");
            if (catalog.categoryName(categoryId).isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "分类不存在");
            }
        } else {
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            for (String value : r.videoIds() == null ? List.<String>of() : r.videoIds()) {
                Long videoId = AppValues.id(value, "影片 ID");
                if (catalog.videoName(videoId).isEmpty()) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "影片 " + videoId + " 不存在");
                }
                ids.add(videoId);
            }
            if (ids.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "手动挑片至少选择一部影片");
            }
            videoIds = String.join(",", ids.stream().map(String::valueOf).toList());
        }
        e.setTitle(r.title().strip());
        e.setSourceType(r.sourceType());
        e.setCategoryId(categoryId);
        e.setVideoIds(videoIds);
        e.setItemLimit(r.itemLimit());
        e.setEnabled(r.enabled());
        e.setSortNo(r.sortNo());
        e.setUpdatedAt(AppValues.now());
        if (id == null) {
            e.setDeleted(0);
            e.setCreatedAt(AppValues.now());
            sections.insert(e);
        } else {
            sections.update(null, Wrappers.<AppSectionEntity>lambdaUpdate()
                    .set(AppSectionEntity::getTitle, e.getTitle()).set(AppSectionEntity::getSourceType, e.getSourceType())
                    .set(AppSectionEntity::getCategoryId, categoryId).set(AppSectionEntity::getVideoIds, videoIds)
                    .set(AppSectionEntity::getItemLimit, e.getItemLimit()).set(AppSectionEntity::getEnabled, e.getEnabled())
                    .set(AppSectionEntity::getSortNo, e.getSortNo()).set(AppSectionEntity::getUpdatedAt, e.getUpdatedAt())
                    .eq(AppSectionEntity::getId, id));
        }
        return sectionResponse(requireSection(e.getId()));
    }

    /**
     * 逻辑删除推荐位。
     */
    @Override
    @Transactional
    public void deleteSection(Long id) {
        requireSection(id);
        sections.deleteById(id);
    }

    /**
     * 解析手动挑片的影片 ID 列表。
     */
    static List<Long> videoIds(String value) {
        List<Long> ids = new ArrayList<>();
        if (value != null) {
            for (String part : value.split(",")) {
                if (!part.isBlank()) {
                    ids.add(Long.valueOf(part.strip()));
                }
            }
        }
        return ids;
    }

    /**
     * 组装推荐位后台响应，附带分类与影片名称。
     */
    private AppSectionResponse sectionResponse(AppSectionEntity e) {
        String categoryName = e.getCategoryId() == null ? null : catalog.categoryName(e.getCategoryId()).orElse(null);
        List<AppPickedVideoResponse> videos = videoIds(e.getVideoIds()).stream()
                .map(videoId -> new AppPickedVideoResponse(videoId.toString(), catalog.videoName(videoId).orElse(null)))
                .toList();
        return converter.section(e, categoryName, videos);
    }

    /**
     * 校验开屏跳转目标：影片必须存在，外部链接必须是 http/https，不跳转时清空。
     */
    private String splashTarget(String type, String value) {
        if (TARGET_VIDEO.equals(type)) {
            Long videoId = AppValues.id(value, "跳转影片 ID");
            if (catalog.videoName(videoId).isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "跳转的影片不存在");
            }
            return videoId.toString();
        }
        return TARGET_URL.equals(type) ? AppValues.httpUrl(value, "广告链接") : null;
    }

    /**
     * 读取开屏跳转影片名称。
     */
    private String splashTargetName(AppSplashEntity e) {
        if (!TARGET_VIDEO.equals(e.getTargetType()) || e.getTargetValue() == null) {
            return null;
        }
        return catalog.videoName(Long.valueOf(e.getTargetValue())).orElse(null);
    }

    /**
     * 读取轮播跳转目标名称。
     */
    private String targetName(AppBannerEntity e) {
        if (e.getTargetId() == null) {
            return null;
        }
        return (TARGET_VIDEO.equals(e.getTargetType()) ? catalog.videoName(e.getTargetId())
                : catalog.categoryName(e.getTargetId())).orElse(null);
    }

    /**
     * 校验图片文件存在。
     */
    private Long requireImage(String value) {
        Long fileId = AppValues.id(value, "图片文件 ID");
        if (!files.exists(fileId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片文件不存在或已删除");
        }
        return fileId;
    }

    /**
     * 读取开屏，不存在返回 404。
     */
    private AppSplashEntity requireSplash(Long id) {
        AppSplashEntity e = splashes.selectById(id);
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "开屏不存在");
        }
        return e;
    }

    /**
     * 读取轮播，不存在返回 404。
     */
    private AppBannerEntity requireBanner(Long id) {
        AppBannerEntity e = banners.selectById(id);
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "轮播不存在");
        }
        return e;
    }

    /**
     * 读取推荐位，不存在返回 404。
     */
    private AppSectionEntity requireSection(Long id) {
        AppSectionEntity e = sections.selectById(id);
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "推荐位不存在");
        }
        return e;
    }
}
