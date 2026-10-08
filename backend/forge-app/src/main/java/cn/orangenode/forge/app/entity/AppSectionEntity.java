package cn.orangenode.forge.app.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * App 首页推荐位记录，对应 {@code app_section}。
 */
@Getter
@Setter
@TableName("app_section")
public class AppSectionEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 推荐行标题。
     */
    private String title;

    /**
     * 数据来源 CATEGORY/MANUAL。
     */
    private String sourceType;

    /**
     * 按分类自动取片时的分类主键。
     */
    private Long categoryId;

    /**
     * 手动挑片的影片主键，英文逗号分隔并保持顺序。
     */
    private String videoIds;

    /**
     * 最多展示条数。
     */
    private Integer itemLimit;

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
