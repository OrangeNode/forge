package cn.orangenode.forge.member.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 普通用户观看历史，对应 {@code member_watch_history}；同一用户同一影片只有一行，记录最近观看位置。
 */
@Getter
@Setter
@TableName("member_watch_history")
public class MemberWatchHistoryEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 普通用户主键。
     */
    private Long memberId;

    /**
     * 影片主键。
     */
    private Long videoId;

    /**
     * 最近观看的播放线路主键，可为空。
     */
    private Long lineId;

    /**
     * 最近观看的分集序号，从 0 开始。
     */
    private Integer episodeIndex;

    /**
     * 最近观看的分集名称。
     */
    private String episodeName;

    /**
     * 播放位置秒数。
     */
    private Integer positionSeconds;

    /**
     * 分集总时长秒数，未知为 0。
     */
    private Integer durationSeconds;

    /**
     * 创建时间（UTC）。
     */
    private LocalDateTime createdAt;

    /**
     * 最近观看时间（UTC）。
     */
    private LocalDateTime updatedAt;
}
