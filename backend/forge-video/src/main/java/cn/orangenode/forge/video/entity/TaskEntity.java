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
 * 视频模块Task数据记录。
 */
@Getter
@Setter
@TableName("video_task")
public class TaskEntity {
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
     * 资源站主键。
     */
    private Long sourceId;

    /**
     * 日周全部范围 DAY WEEK ALL。
     */
    private String rangeCode;

    /**
     * 源站分类编号。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String upstreamCategoryId;

    /**
     * 独立执行频率 INTERVAL DAILY WEEKLY。
     */
    private String frequency;

    /**
     * 按小时执行间隔。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer intervalHours;

    /**
     * 周一至周日编号一至七。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer weekday;

    /**
     * 本地执行时间 HH:mm。
     */
    private String timeOfDay;

    /**
     * 执行时区。
     */
    private String zoneId;

    /**
     * 启用状态。
     */
    private Boolean enabled;

    /**
     * 下次执行时刻 UTC。
     */
    private LocalDateTime nextRunAt;

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
