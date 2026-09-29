package cn.orangenode.forge.system.service;

import java.util.List;

/**
 * 管理员权限与角色解析。
 *
 * <p>认证过滤链与身份接口共用同一处解析规则，保证“前端看到的权限”和“后端校验的权限”
 * 来自同一份数据与同一条超级管理员语义。</p>
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
}
