package cn.orangenode.forge.framework.security;

import java.util.List;
import java.util.Optional;

/**
 * 管理员账号目录端口。
 *
 * <p>认证过滤链只依赖本接口，不依赖账号模块的实现：由拥有管理员表的业务模块提供实现，
 * 启动时完成装配，因此 framework 与业务模块之间没有反向依赖，也不形成循环。</p>
 *
 * <p>实现必须读取当前数据库状态，不缓存账号状态与权限：停用、改密、权限调整
 * 都要在下一次请求立即生效。</p>
 */
public interface AdminAccountDirectory {

    /**
     * 按管理员 ID 查询账号视图。
     *
     * @param adminId 管理员 ID
     * @return 账号视图，账号不存在时为空
     */
    Optional<AdminAccountView> findById(Long adminId);

    /**
     * 查询管理员当前拥有的权限代码。
     *
     * @param adminId 管理员 ID
     * @return 权限代码集合，格式为 {@code 模块:资源:动作}；没有权限时返回空集合
     */
    List<String> loadAuthorityCodes(Long adminId);
}
