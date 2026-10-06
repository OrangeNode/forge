package cn.orangenode.forge.video.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 视频模块Origin数据记录。
 */
@Getter
@Setter
@TableName("video_origin")
public class OriginEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 资源站主键。
     */
    private Long sourceId;

    /**
     * 源站记录编号。
     */
    private String upstreamId;

    /**
     * 本地影片主键。
     */
    private Long videoId;

    /**
     * 源站分类编号。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String upstreamCategoryId;

    /**
     * 名称。
     */
    private String name;

    /**
     * 年份，允许未知。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer year;

    /**
     * 地区。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String area;

    /**
     * 语言。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String language;

    /**
     * 导演人员文本。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String director;

    /**
     * 演员人员文本。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String actors;

    /**
     * 源站声明更新时间 UTC。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime sourceUpdatedAt;

    /**
     * 最近采集时间 UTC。
     */
    private LocalDateTime collectedAt;

    /**
     * 创建时间，UTC。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，UTC。
     */
    private LocalDateTime updatedAt;
}
