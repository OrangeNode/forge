package cn.orangenode.forge.app.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * App 意见反馈与播放报错，对应 {@code app_feedback}；只追加，处理时更新状态。
 */
@Getter
@Setter
@TableName("app_feedback")
public class AppFeedbackEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 提交人普通用户主键。
     */
    private Long memberId;

    /**
     * 提交人用户名快照。
     */
    private String memberUsername;

    /**
     * 类型：PLAYBACK 播放报错 / GENERAL 意见反馈。
     */
    private String feedbackType;

    /**
     * 影片主键，播放报错必填。
     */
    private Long videoId;

    /**
     * 影片名称快照。
     */
    private String videoName;

    /**
     * 播放线路主键。
     */
    private Long lineId;

    /**
     * 线路名称快照。
     */
    private String lineName;

    /**
     * 分集名称。
     */
    private String episodeName;

    /**
     * 反馈内容。
     */
    private String content;

    /**
     * 提交设备名称，仅展示。
     */
    private String deviceName;

    /**
     * App 版本，仅展示。
     */
    private String appVersion;

    /**
     * 状态：PENDING 待处理 / RESOLVED 已处理。
     */
    private String status;

    /**
     * 处理备注。
     */
    private String handleRemark;

    /**
     * 处理管理员主键。
     */
    private Long handledBy;

    /**
     * 处理时间（UTC）。
     */
    private LocalDateTime handledAt;

    /**
     * 提交时间（UTC）。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间（UTC）。
     */
    private LocalDateTime updatedAt;
}
