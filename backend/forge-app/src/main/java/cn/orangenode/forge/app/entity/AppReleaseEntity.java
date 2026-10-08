package cn.orangenode.forge.app.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * App 版本更新，对应 {@code app_release}，固定单行 {@code id = 1}；手机与电视共用一个 APK。
 */
@Getter
@Setter
@TableName("app_release")
public class AppReleaseEntity {
    /**
     * 固定为 1。
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 最新版本号（Android versionCode），为空表示未发布。
     */
    private Integer versionCode;

    /**
     * 最新版本名。
     */
    private String versionName;

    /**
     * 最低可用版本号，低于该值强制更新；为空表示不强制。
     */
    private Integer minVersionCode;

    /**
     * APK 包名。
     */
    private String packageName;

    /**
     * APK 文件主键。
     */
    private Long apkFileId;

    /**
     * APK 公开下载路径（不含域名）。
     */
    private String apkUrl;

    /**
     * APK 的 SHA-256。
     */
    private String apkSha256;

    /**
     * APK 字节数。
     */
    private Long apkSize;

    /**
     * 更新说明。
     */
    private String changelog;

    /**
     * 最后修改管理员主键。
     */
    private Long updatedBy;

    /**
     * 创建时间（UTC）。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间（UTC）。
     */
    private LocalDateTime updatedAt;
}
