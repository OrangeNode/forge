package cn.orangenode.forge.app.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * App 开屏记录，对应 {@code app_splash}。
 */
@Getter
@Setter
@TableName("app_splash")
public class AppSplashEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 名称，仅后台展示。
     */
    private String name;

    /**
     * 封面图文件主键。
     */
    private Long imageFileId;

    /**
     * 电视端封面图文件主键，为空时电视端使用手机封面图。
     */
    private Long tvImageFileId;

    /**
     * 停留秒数。
     */
    private Integer durationSeconds;

    /**
     * 是否可跳过。
     */
    private Boolean skippable;

    /**
     * 点击跳转类型：NONE 不跳转 / VIDEO 影片详情 / URL 外部链接。
     */
    private String targetType;

    /**
     * 跳转影片主键或外部链接。
     */
    private String targetValue;

    /**
     * 生效开始时间，UTC，空表示立即。
     */
    private LocalDateTime startAt;

    /**
     * 生效结束时间，UTC，空表示长期。
     */
    private LocalDateTime endAt;

    /**
     * 是否启用。
     */
    private Boolean enabled;

    /**
     * 显示顺序，越小越靠前。
     */
    private Integer sortNo;

    /**
     * 逻辑删除标记。
     */
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间，UTC。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，UTC。
     */
    private LocalDateTime updatedAt;
}
