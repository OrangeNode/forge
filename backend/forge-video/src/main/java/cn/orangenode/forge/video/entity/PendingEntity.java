package cn.orangenode.forge.video.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 视频模块Pending数据记录。
 */
@Getter
@Setter
@TableName("video_pending")
public class PendingEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 关联运行，历史日志清理后可为空。
     */
    private Long runId;

    /**
     * 资源站主键。
     */
    private Long sourceId;

    /**
     * 源站记录编号。
     */
    private String upstreamId;

    /**
     * 待确认采集数据快照 JSON。
     */
    private String payload;

    /**
     * 候选影片主键列表。
     */
    private String candidateIds;

    /**
     * 存疑原因。
     */
    private String reason;

    /**
     * 业务处理状态。
     */
    private String status;

    /**
     * 创建时间，UTC。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，UTC。
     */
    private LocalDateTime updatedAt;
}
