package cn.orangenode.forge.framework.member;

import java.time.Instant;

/**
 * 已认证普通用户主体。
 *
 * <p>与管理员主体 {@code AuthenticatedAdmin} 是不同类型，两种身份不能互相替代；
 * 当前用户 ID 与设备只能从本主体读取，不接受客户端提交。</p>
 *
 * @param id          普通用户 ID
 * @param username    用户名
 * @param nickname    昵称
 * @param vipExpireAt VIP 到期时刻，可为空
 * @param deviceId    当前令牌对应的登录设备记录 ID
 */
public record AuthenticatedMember(Long id, String username, String nickname, Instant vipExpireAt, Long deviceId) {

    /**
     * 判断 VIP 在给定时刻是否有效。
     *
     * @param now 判断时刻
     * @return 到期时刻晚于判断时刻时为 {@code true}
     */
    public boolean vipActiveAt(Instant now) {
        return vipExpireAt != null && vipExpireAt.isAfter(now);
    }
}
