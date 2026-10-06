package cn.orangenode.forge.video.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 视频模块Source数据记录。
 */
@Getter
@Setter
@TableName("video_source")
public class SourceEntity {
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
     * MacCMS JSON 接口地址。
     */
    private String apiUrl;

    /**
     * 启用状态。
     */
    private Boolean enabled;

    /**
     * 源站更新时间使用的时区。
     */
    private String sourceZone;

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
