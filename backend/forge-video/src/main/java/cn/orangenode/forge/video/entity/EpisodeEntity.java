package cn.orangenode.forge.video.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 视频模块Episode数据记录。
 */
@Getter
@Setter
@TableName("video_episode")
public class EpisodeEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 播放线路主键。
     */
    private Long lineId;

    /**
     * 名称。
     */
    private String name;

    /**
     * 显示顺序。
     */
    private Integer sortNo;

    /**
     * 完整分集地址，保留参数及编码。
     */
    private String url;

    /**
     * 媒体地址类型 M3U8 MP4 OTHER。
     */
    private String urlType;

    /**
     * 创建时间，UTC。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，UTC。
     */
    private LocalDateTime updatedAt;
}
