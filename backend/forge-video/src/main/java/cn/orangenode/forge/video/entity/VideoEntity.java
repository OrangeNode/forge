package cn.orangenode.forge.video.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 视频模块Video数据记录。
 */
@Getter
@Setter
@TableName("video_video")
public class VideoEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 名称。
     */
    private String name;

    /**
     * 规范化匹配片名。
     */
    private String matchName;

    /**
     * 片名拼音首字母，为空表示尚未回填。
     */
    private String nameInitials;

    /**
     * 本地分类主键。
     */
    private Long categoryId;

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
     * 远程封面地址。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String posterUrl;

    /**
     * 纯文本简介。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;

    /**
     * 源站更新说明。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remarks;

    /**
     * 源站声明总集数，非实际地址数量。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer declaredTotal;

    /**
     * 源站完结声明，允许未知。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean ended;

    /**
     * 主要来源记录主键。
     */
    private Long primaryOriginId;

    /**
     * 影片上架状态。
     */
    private Boolean published;

    /**
     * 手工资料更新锁定。
     */
    private Boolean metadataLocked;

    /**
     * 创建时间，UTC。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，UTC。
     */
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记。
     */
    @TableLogic
    private Integer deleted;
}
