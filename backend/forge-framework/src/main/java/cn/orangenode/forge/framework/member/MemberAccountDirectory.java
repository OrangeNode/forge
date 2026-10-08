package cn.orangenode.forge.framework.member;

import java.util.Optional;

/**
 * 普通用户账号目录端口。
 *
 * <p>普通用户认证过滤器只依赖本接口，由拥有普通用户表的业务模块实现，
 * 因此基础模块不反向依赖业务模块；未装配实现时 App 受保护接口一律视为未登录。</p>
 */
public interface MemberAccountDirectory {

    /**
     * 按普通用户 ID 读取当前账号状态。
     *
     * @param memberId 普通用户 ID
     * @return 账号视图，不存在时为空
     */
    Optional<MemberAccountView> findById(Long memberId);

    /**
     * 记录登录设备的最近活跃时间与来源地址，只在令牌续期时调用。
     *
     * @param deviceId 登录设备记录 ID
     * @param clientIp 当前来源地址，可为空
     */
    void recordActivity(Long deviceId, String clientIp);
}
