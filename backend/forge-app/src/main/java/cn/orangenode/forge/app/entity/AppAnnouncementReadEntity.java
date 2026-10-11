package cn.orangenode.forge.app.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 普通用户已看过的公告版本，对应 {@code app_announcement_read}，主键为用户与公告联合主键。
 */
@Getter
@Setter
@TableName("app_announcement_read")
public class AppAnnouncementReadEntity {
    /**
     * 普通用户主键。
     */
    private Long memberId;

    /**
     * 公告主键。
     */
    private Long announcementId;

    /**
     * 已看过的内容版本。
     */
    private Integer revision;

    /**
     * 最近关闭时间，UTC。
     */
    private LocalDateTime readAt;
}
