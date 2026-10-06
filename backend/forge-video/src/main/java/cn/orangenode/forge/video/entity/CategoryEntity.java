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
 * 视频模块Category数据记录。
 */
@Getter
@Setter
@TableName("video_category")
public class CategoryEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 父分类主键，顶级使用零，与分类树查询一致。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentId;

    /**
     * 名称。
     */
    private String name;

    /**
     * 显示顺序。
     */
    private Integer sortNo;

    /**
     * 启用状态。
     */
    private Boolean enabled;

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
