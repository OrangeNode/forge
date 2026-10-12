package cn.orangenode.forge.app.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * App 公告，对应 {@code app_announcement}。
 */
@Getter
@Setter
@TableName("app_announcement")
public class AppAnnouncementEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 标题。
     */
    private String title;

    /**
     * 内容格式：TEXT 纯文本 / HTML。
     */
    private String contentType;

    /**
     * 内容；HTML 为白名单过滤后的结果。
     */
    private String content;

    /**
     * 内容版本，标题、内容或按钮修改后加一。
     */
    private Integer revision;

    /**
     * “去了解”按钮：NONE 不显示 / VIDEO 影片详情 / URL 外部链接。
     */
    private String actionType;

    /**
     * 跳转影片主键或外部链接。
     */
    private String actionValue;

    /**
     * 弹出频率：ONCE 每个用户只弹一次 / DAILY 每天一次 / EVERY_LAUNCH 每次进入 App。
     */
    private String popupFrequency;

    /**
     * 生效开始时间，UTC，空表示立即。
     */
    private LocalDateTime startAt;

    /**
     * 生效结束时间，UTC，空表示长期。
     */
    private LocalDateTime endAt;

    /**
     * 是否启用。
     */
    private Boolean enabled;

    /**
     * 优先级，越小越优先。
     */
    private Integer sortNo;

    /**
     * 逻辑删除标记。
     */
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间，UTC。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，UTC。
     */
    private LocalDateTime updatedAt;
}
