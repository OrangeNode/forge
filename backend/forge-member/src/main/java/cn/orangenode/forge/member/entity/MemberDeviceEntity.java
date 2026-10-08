package cn.orangenode.forge.member.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 普通用户登录设备记录，对应 {@code member_device}；同一账号同一设备编号只有一行。
 */
@Getter
@Setter
@TableName("member_device")
public class MemberDeviceEntity {
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
     * App 首次安装生成的设备编号。
     */
    private String deviceCode;

    /**
     * 设备名称，仅展示。
     */
    private String deviceName;

    /**
     * 最近来源地址，仅展示。
     */
    private String clientIp;

    /**
     * 当前令牌摘要，离线后清空。
     */
    private String tokenDigest;

    /**
     * 是否在线。
     */
    private Boolean online;

    /**
     * 离线原因代码。
     */
    private String offlineReason;

    /**
     * 最近登录时间，UTC。
     */
    private LocalDateTime loginAt;

    /**
     * 最近活跃时间，UTC。
     */
    private LocalDateTime lastActiveAt;

    /**
     * 离线时间，UTC。
     */
    private LocalDateTime offlineAt;

    /**
     * 创建时间，UTC。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，UTC。
     */
    private LocalDateTime updatedAt;
}
