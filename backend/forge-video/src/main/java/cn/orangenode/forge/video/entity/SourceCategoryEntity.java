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
 * 视频模块SourceCategory数据记录。
 */
@Getter
@Setter
@TableName("video_source_category")
public class SourceCategoryEntity {
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
     * 名称。
     */
    private String name;

    /**
     * 源站父分类编号。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String upstreamParentId;

    /**
     * 本地分类主键。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long categoryId;

    /**
     * 创建时间，UTC。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，UTC。
     */
    private LocalDateTime updatedAt;
}
