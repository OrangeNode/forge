package cn.orangenode.forge.video.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 视频模块Run数据记录。
 */
@Getter
@Setter
@TableName("video_run")
public class RunEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 定时任务主键，手动运行为空。
     */
    private Long taskId;

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
    private String upstreamCategoryId;

    /**
     * 勾选采集的源站影片编号，逗号分隔；为空表示按范围与分类采集。
     */
    private String upstreamIds;

    /**
     * 业务处理状态。
     */
    private String status;

    /**
     * 下次待处理页面断点。
     */
    private Integer nextPage;

    /**
     * 源站返回总页数。
     */
    private Integer pageCount;

    /**
     * 累计处理条数。
     */
    private Long processed;

    /**
     * 累计新建影片数。
     */
    private Long inserted;

    /**
     * 累计更新来源数。
     */
    private Long updated;

    /**
     * 累计存疑条数。
     */
    private Long pending;

    /**
     * 累计跳过条数。
     */
    private Long skipped;

    /**
     * 失败原因，不含原始响应和凭据。
     */
    private String errorMessage;

    /**
     * 错过窗口提示。
     */
    private String warning;

    /**
     * 首次开始时间 UTC。
     */
    private LocalDateTime startedAt;

    /**
     * 结束时间 UTC。
     */
    private LocalDateTime finishedAt;

    /**
     * 运行采用的采集规则快照 JSON。
     */
    private String settingsSnapshot;

    /**
     * 创建时间，UTC。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，UTC。
     */
    private LocalDateTime updatedAt;
}
