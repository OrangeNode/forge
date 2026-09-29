package cn.orangenode.forge.system.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import cn.orangenode.forge.framework.config.ForgeSecurityProperties;
import cn.orangenode.forge.system.mapper.SysPermissionMapper;
import cn.orangenode.forge.system.mapper.SysRoleMapper;
import cn.orangenode.forge.system.service.AdminAuthorityService;

/**
 * 管理员权限与角色解析实现。
 *
 * <p>不缓存解析结果：权限数据量小，按请求查询可以直接保证停用、改密与权限调整
 * 在下一次请求立即生效，也不需要额外的缓存失效通道。出现真实性能压力时，
 * 再与权限写入接口一起引入带失效的缓存，并按当时的数据量验证。</p>
 */
@Service
public class AdminAuthorityServiceImpl implements AdminAuthorityService {

    /**
     * 角色数据访问。
     */
    private final SysRoleMapper roleMapper;

    /**
     * 权限数据访问。
     */
    private final SysPermissionMapper permissionMapper;

    /**
     * 认证与权限配置，提供超级管理员角色代码。
     */
    private final ForgeSecurityProperties securityProperties;

    /**
     * 构造权限解析实现。
     *
     * @param roleMapper        角色数据访问
     * @param permissionMapper  权限数据访问
     * @param securityProperties 认证与权限配置
     */
    public AdminAuthorityServiceImpl(SysRoleMapper roleMapper, SysPermissionMapper permissionMapper,
            ForgeSecurityProperties securityProperties) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.securityProperties = securityProperties;
    }

    /**
     * 解析管理员当前拥有的权限代码。
     *
     * <p>拥有超级管理员角色的管理员获得全部有效权限：初始化引导创建的第一个管理员
     * 必须能够管理工作台自身，否则空库安装后没有任何途径授权。</p>
     *
     * @param adminId 管理员 ID
     * @return 权限代码列表
     */
    @Override
    public List<String> loadPermissionCodes(Long adminId) {
        if (isSuperAdmin(adminId)) {
            return permissionMapper.selectAllPermissionCodes();
        }
        return permissionMapper.selectPermissionCodesByAdminId(adminId);
    }

    /**
     * 解析管理员当前拥有的角色代码。
     *
     * @param adminId 管理员 ID
     * @return 角色代码列表
     */
    @Override
    public List<String> loadRoleCodes(Long adminId) {
        return roleMapper.selectRoleCodesByAdminId(adminId);
    }

    /**
     * 判断管理员是否拥有超级管理员角色。
     *
     * <p>角色代码比较不区分大小写，避免配置大小写差异导致超级管理员失去权限。</p>
     *
     * @param adminId 管理员 ID
     * @return 拥有超级管理员角色时返回 {@code true}
     */
    @Override
    public boolean isSuperAdmin(Long adminId) {
        String superRoleCode = securityProperties.getSuperRoleCode();
        return roleMapper.selectRoleCodesByAdminId(adminId).stream()
                .anyMatch(code -> code != null && code.equalsIgnoreCase(superRoleCode));
    }
}
