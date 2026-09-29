package cn.orangenode.forge.system.service;

import java.util.List;

/**
 * 管理员权限与角色解析。
 *
 * <p>认证过滤链与身份接口共用同一处解析规则，保证“前端看到的权限”和“后端校验的权限”
 * 来自同一份数据与同一条超级管理员语义。</p>
 *
 * <p>权限解析结果按管理员缓存，缓存键包含全局权限版本：任何角色、权限、菜单可见性或
 * 管理员角色关系变更后递增版本，使全部既有缓存立即失效，旧键按存活时间自然过期，
 * 不需要通配扫描删除。权限的数据库提交成功后才递增版本，不缓存未提交状态。</p>
 */
public interface AdminAuthorityService {

    /**
     * 解析管理员当前拥有的权限代码。
     *
     * @param adminId 管理员 ID
     * @return 权限代码列表，没有权限时返回空列表
     */
    List<String> loadPermissionCodes(Long adminId);

    /**
     * 解析管理员当前拥有的角色代码。
     *
     * @param adminId 管理员 ID
     * @return 角色代码列表，没有角色时返回空列表
     */
    List<String> loadRoleCodes(Long adminId);

    /**
     * 判断管理员是否拥有超级管理员角色。
     *
     * <p>超级管理员角色按配置识别：权限解析与菜单可见性都用同一判断，
     * 保证初始化引导创建的管理员既能看到全部菜单，也能调用全部受保护接口。</p>
     *
     * @param adminId 管理员 ID
     * @return 拥有超级管理员角色时返回 {@code true}
     */
    boolean isSuperAdmin(Long adminId);

    /**
     * 读取当前全局权限版本。
     *
     * <p>版本键不存在时视为初始版本 {@code 0}，与版本递增使用同一个键，
     * 因此权限缓存只在同一版本内命中。Redis 不可用时异常向上抛出，
     * 由统一错误出口返回“依赖暂不可用”，不静默绕过缓存继续执行。</p>
     *
     * @return 当前全局权限版本，键不存在时为 0
     */
    long currentPermissionVersion();

    /**
     * 使全部权限缓存立即失效。
     *
     * <p>实现方式为递增全局权限版本键：权限缓存键包含版本，因此递增后所有旧键都不再被读取，
     * 新请求按新版本重新解析并写入缓存，旧键按存活时间自然过期。
     * 调用方必须在角色、权限、菜单可见性或管理员角色关系的数据变更提交成功后调用。</p>
     */
    void evictAllPermissions();
}
