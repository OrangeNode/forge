package cn.orangenode.forge.app.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * App 全局设置单行记录，对应 {@code app_settings}。
 */
@Getter
@Setter
@TableName("app_settings")
public class AppSettingsEntity {
    /**
     * 固定为 1 的单行主键。
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * VIP 开关：开启时只有 VIP 能观看。
     */
    private Boolean vipRequired;

    /**
     * 是否开放注册。
     */
    private Boolean registrationOpen;

    /**
     * 同一账号同时登录设备上限。
     */
    private Integer maxDevices;

    /**
     * 客服文字。
     */
    private String serviceText;

    /**
     * 公告文字。
     */
    private String noticeText;

    /**
     * 手动热搜词 JSON 数组。
     */
    private String hotWords;

    /**
     * 热搜屏蔽词 JSON 数组。
     */
    private String blockedWords;

    /**
     * 最近修改管理员主键。
     */
    private Long updatedBy;

    /**
     * 创建时间，UTC。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，UTC。
     */
    private LocalDateTime updatedAt;
}
