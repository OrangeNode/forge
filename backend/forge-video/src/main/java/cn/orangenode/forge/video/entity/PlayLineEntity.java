package cn.orangenode.forge.video.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 视频模块PlayLine数据记录。
 */
@Getter
@Setter
@TableName("video_play_line")
public class PlayLineEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 来源记录主键。
     */
    private Long originId;

    /**
     * 原始播放线路标识。
     */
    private String code;

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
}
