package cn.orangenode.forge.app.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * App 首页轮播记录，对应 {@code app_banner}。
 */
@Getter
@Setter
@TableName("app_banner")
public class AppBannerEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 标题。
     */
    private String title;

    /**
     * 图片文件主键。
     */
    private Long imageFileId;

    /**
     * 跳转类型 NONE/VIDEO/CATEGORY。
     */
    private String targetType;

    /**
     * 跳转影片或分类主键。
     */
    private Long targetId;

    /**
     * 跳转类型为 URL 时的外部链接。
     */
    private String targetUrl;

    /**
     * 上线时间，UTC，空表示立即。
     */
    private LocalDateTime startAt;

    /**
     * 下线时间，UTC，空表示长期。
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
