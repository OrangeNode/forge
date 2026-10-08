package cn.orangenode.forge.app.service.impl;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.orangenode.forge.app.config.AppProperties;
import cn.orangenode.forge.app.converter.AppConverter;
import cn.orangenode.forge.app.entity.AppReleaseEntity;
import cn.orangenode.forge.app.mapper.AppReleaseMapper;
import cn.orangenode.forge.app.request.AppReleaseMinVersionRequest;
import cn.orangenode.forge.app.request.AppReleasePublishRequest;
import cn.orangenode.forge.app.response.AppReleaseResponse;
import cn.orangenode.forge.app.service.AppReleaseService;
import cn.orangenode.forge.app.support.ApkInspector;
import cn.orangenode.forge.app.support.AppValues;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.storage.StoredFileContent;
import cn.orangenode.forge.framework.storage.StoredFileReader;

import lombok.RequiredArgsConstructor;

/**
 * 版本更新实现。
 *
 * <p>发布时从文件模块读取 APK，解析包名与版本号并计算 SHA-256：包名必须等于配置的
 * {@code forge.app.package-name}，版本号必须高于当前已发布版本，否则拒绝；通过后经文件读取端口生成公开外链。
 * 版本配置是固定主键的单行，读取不到时视为迁移缺失。</p>
 */
@Service
@RequiredArgsConstructor
public class AppReleaseServiceImpl implements AppReleaseService {

    /**
     * 版本配置固定主键。
     */
    static final long RELEASE_ID = 1L;

    private final AppReleaseMapper releases;
    private final StoredFileReader files;
    private final AppConverter converter;
    private final AppProperties properties;

    /**
     * 读取当前版本配置。
     */
    @Override
    public AppReleaseResponse current() {
        return converter.release(require(), properties.getPackageName());
    }

    /**
     * 解析 APK 并发布新版本；强制更新时最低可用版本同步为新版本号，否则保留原最低可用版本。
     *
     * <p>不声明事务：读取并解析安装包可能耗时较长，不应占用数据库连接；最终写入是单条更新，自身具备原子性。</p>
     */
    @Override
    public AppReleaseResponse publish(AppReleasePublishRequest request, Long operatorId) {
        Long fileId = AppValues.id(request.fileId(), "APK 文件 ID");
        if (!files.exists(fileId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "APK 文件不存在或已删除");
        }
        ApkInspector.ApkInfo apk = inspect(fileId);
        if (!properties.getPackageName().equals(apk.packageName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "安装包包名 " + apk.packageName() + " 与 App 包名 " + properties.getPackageName() + " 不一致");
        }
        AppReleaseEntity current = require();
        if (current.getVersionCode() != null && apk.versionCode() <= current.getVersionCode()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "安装包版本号 " + apk.versionCode()
                    + " 不高于当前已发布的版本号 " + current.getVersionCode() + "，请先提高版本号再打包");
        }
        String apkUrl = files.publicPath(fileId);
        Integer minVersion = Boolean.TRUE.equals(request.force()) ? Integer.valueOf(apk.versionCode())
                : current.getMinVersionCode();
        releases.update(null, Wrappers.<AppReleaseEntity>lambdaUpdate()
                .set(AppReleaseEntity::getVersionCode, apk.versionCode())
                .set(AppReleaseEntity::getVersionName, apk.versionName())
                .set(AppReleaseEntity::getMinVersionCode, minVersion)
                .set(AppReleaseEntity::getPackageName, apk.packageName())
                .set(AppReleaseEntity::getApkFileId, fileId)
                .set(AppReleaseEntity::getApkUrl, apkUrl)
                .set(AppReleaseEntity::getApkSha256, apk.sha256())
                .set(AppReleaseEntity::getApkSize, apk.sizeBytes())
                .set(AppReleaseEntity::getChangelog, AppValues.clean(request.changelog()))
                .set(AppReleaseEntity::getUpdatedBy, operatorId)
                .set(AppReleaseEntity::getUpdatedAt, AppValues.now())
                .eq(AppReleaseEntity::getId, RELEASE_ID));
        return current();
    }

    /**
     * 调整最低可用版本，不能高于最新版本号。
     */
    @Override
    @Transactional
    public AppReleaseResponse updateMinVersion(AppReleaseMinVersionRequest request, Long operatorId) {
        AppReleaseEntity current = require();
        if (current.getVersionCode() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "尚未发布版本");
        }
        Integer minVersion = request.minVersionCode();
        if (minVersion != null && minVersion > current.getVersionCode()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "最低可用版本不能高于最新版本号 " + current.getVersionCode());
        }
        releases.update(null, Wrappers.<AppReleaseEntity>lambdaUpdate()
                .set(AppReleaseEntity::getMinVersionCode, minVersion)
                .set(AppReleaseEntity::getUpdatedBy, operatorId)
                .set(AppReleaseEntity::getUpdatedAt, AppValues.now())
                .eq(AppReleaseEntity::getId, RELEASE_ID));
        return current();
    }

    /**
     * 撤销发布：清空全部版本字段，APK 文件与外链保留在文件管理中。
     */
    @Override
    @Transactional
    public AppReleaseResponse withdraw(Long operatorId) {
        require();
        releases.update(null, Wrappers.<AppReleaseEntity>lambdaUpdate()
                .set(AppReleaseEntity::getVersionCode, null)
                .set(AppReleaseEntity::getVersionName, null)
                .set(AppReleaseEntity::getMinVersionCode, null)
                .set(AppReleaseEntity::getPackageName, null)
                .set(AppReleaseEntity::getApkFileId, null)
                .set(AppReleaseEntity::getApkUrl, null)
                .set(AppReleaseEntity::getApkSha256, null)
                .set(AppReleaseEntity::getApkSize, null)
                .set(AppReleaseEntity::getChangelog, null)
                .set(AppReleaseEntity::getUpdatedBy, operatorId)
                .set(AppReleaseEntity::getUpdatedAt, AppValues.now())
                .eq(AppReleaseEntity::getId, RELEASE_ID));
        return current();
    }

    /**
     * 读取并解析 APK 文件内容。
     */
    private ApkInspector.ApkInfo inspect(Long fileId) {
        StoredFileContent content = files.open(fileId);
        try (InputStream stream = content.stream()) {
            return ApkInspector.inspect(stream);
        } catch (IOException failure) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取 APK 文件失败，请重试", failure);
        }
    }

    /**
     * 读取版本配置单行。
     */
    private AppReleaseEntity require() {
        AppReleaseEntity entity = releases.selectById(RELEASE_ID);
        if (entity == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "版本配置缺失，请检查数据库迁移");
        }
        return entity;
    }
}
