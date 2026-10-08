package cn.orangenode.forge.member.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 普通用户账号记录，对应 {@code member_user}。
 */
@Getter
@Setter
@TableName("member_user")
public class MemberUserEntity {
    /**
     * 数据库自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名，不区分大小写唯一。
     */
    private String username;

    /**
     * 密码编码结果，不对外返回。
     */
    private String passwordHash;

    /**
     * 昵称。
     */
    private String nickname;

    /**
     * 头像地址。
     */
    private String avatarUrl;

    /**
     * 账号状态代码。
     */
    private String status;

    /**
     * VIP 到期时间，UTC。
     */
    private LocalDateTime vipExpireAt;

    /**
     * 会话版本，递增后此前令牌全部失效。
     */
    private Long sessionVersion;

    /**
     * 注册来源地址。
     */
    private String registerIp;

    /**
     * 最近登录时间，UTC。
     */
    private LocalDateTime lastLoginAt;

    /**
     * 最近登录来源地址。
     */
    private String lastLoginIp;

    /**
     * 创建时间，UTC。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，UTC。
     */
    private LocalDateTime updatedAt;
}
