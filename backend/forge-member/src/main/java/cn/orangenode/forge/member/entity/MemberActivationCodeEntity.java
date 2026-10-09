package cn.orangenode.forge.member.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 注册激活码，对应 {@code member_activation_code}；一码只能注册一次，与 VIP 无关。
 */
@Getter
@Setter
@TableName("member_activation_code")
public class MemberActivationCodeEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 激活码，大写字母与数字，不含分隔符。
     */
    private String code;

    /**
     * 状态代码 UNUSED/USED/REVOKED。
     */
    private String status;

    /**
     * 有效期截止时间，UTC，空表示长期有效。
     */
    private LocalDateTime expireAt;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 使用该码注册的普通用户主键。
     */
    private Long usedMemberId;

    /**
     * 使用该码注册的用户名。
     */
    private String usedUsername;

    /**
     * 使用时间，UTC。
     */
    private LocalDateTime usedAt;

    /**
     * 注册来源地址。
     */
    private String usedIp;

    /**
     * 作废时间，UTC。
     */
    private LocalDateTime revokedAt;

    /**
     * 生成该码的管理员主键。
     */
    private Long createdBy;

    /**
     * 创建时间，UTC。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，UTC。
     */
    private LocalDateTime updatedAt;
}
