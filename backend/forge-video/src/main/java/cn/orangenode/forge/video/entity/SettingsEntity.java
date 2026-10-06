package cn.orangenode.forge.video.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 视频模块Settings数据记录。
 */
@Getter
@Setter
@TableName("video_settings")
public class SettingsEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 全局严格去重字段列表。
     */
    private String matchFields;

    /**
     * 主要来源允许更新字段列表。
     */
    private String updateFields;

    /**
     * 新影片默认自动上架。
     */
    private Boolean autoPublish;

    /**
     * 各字段缺失时的策略，JSON 对象。
     */
    private String missingPolicies;

    /**
     * 创建时间，UTC。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，UTC。
     */
    private LocalDateTime updatedAt;
}
