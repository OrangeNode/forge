package cn.orangenode.forge.m3;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import cn.orangenode.forge.system.entity.SysAdminEntity;
import cn.orangenode.forge.system.entity.SysMenuEntity;
import cn.orangenode.forge.system.entity.SysPermissionEntity;
import cn.orangenode.forge.system.entity.SysRoleEntity;
import cn.orangenode.forge.system.mapper.SysAdminMapper;
import cn.orangenode.forge.system.mapper.SysAdminRoleMapper;
import cn.orangenode.forge.system.mapper.SysMenuMapper;
import cn.orangenode.forge.system.mapper.SysPermissionMapper;
import cn.orangenode.forge.system.mapper.SysRoleMapper;

/**
 * M3 端到端验证的数据夹具。
 *
 * <p>账号、角色、菜单与权限通过生产 Mapper 写入，密码同样按真实编码规则落库，
 * 因此登录用例验证的是完整凭据校验路径，而不是绕过编码的比较。</p>
 *
 * <p>角色与权限、角色与菜单的关系表目前没有生产写入接口（管理接口随 M5 实现），
 * 因此这里直接用 JDBC 建立关系，不给生产代码添加无人使用的 Mapper。</p>
 */
@Service
public class M3FixtureService {

    /**
     * 管理员账号数据访问。
     */
    private final SysAdminMapper adminMapper;

    /**
     * 角色数据访问。
     */
    private final SysRoleMapper roleMapper;

    /**
     * 菜单数据访问。
     */
    private final SysMenuMapper menuMapper;

    /**
     * 权限数据访问。
     */
    private final SysPermissionMapper permissionMapper;

    /**
     * 管理员与角色关系数据访问。
     */
    private final SysAdminRoleMapper adminRoleMapper;

    /**
     * 密码编码器。
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * 用于建立与解除关系表的 JDBC 模板。
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造测试数据夹具。
     *
     * @param adminMapper      管理员账号数据访问
     * @param roleMapper       角色数据访问
     * @param menuMapper       菜单数据访问
     * @param permissionMapper 权限数据访问
     * @param adminRoleMapper  管理员与角色关系数据访问
     * @param passwordEncoder  密码编码器
     * @param jdbcTemplate     关系表使用的 JDBC 模板
     */
    public M3FixtureService(SysAdminMapper adminMapper, SysRoleMapper roleMapper, SysMenuMapper menuMapper,
            SysPermissionMapper permissionMapper, SysAdminRoleMapper adminRoleMapper, PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate) {
        this.adminMapper = adminMapper;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.permissionMapper = permissionMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 创建管理员账号，密码按真实编码规则落库。
     *
     * @param username    登录用户名
     * @param rawPassword 密码明文，只在测试中提供
     * @param status      账号状态代码
     * @return 管理员 ID
     */
    public Long createAdmin(String username, String rawPassword, String status) {
        LocalDateTime now = now();
        SysAdminEntity admin = new SysAdminEntity();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setDisplayName(M3TestSupport.ADMIN_DISPLAY_NAME);
        admin.setStatus(status);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        adminMapper.insert(admin);
        return admin.getId();
    }

    /**
     * 创建角色。
     *
     * @param code 角色代码
     * @param name 角色名称
     * @return 角色 ID
     */
    public Long createRole(String code, String name) {
        LocalDateTime now = now();
        SysRoleEntity role = new SysRoleEntity();
        role.setCode(code);
        role.setName(name);
        role.setSortNo(0);
        role.setDeleted(0);
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        roleMapper.insert(role);
        return role.getId();
    }

    /**
     * 创建权限。
     *
     * @param code 权限代码
     * @param name 权限名称
     * @return 权限 ID
     */
    public Long createPermission(String code, String name) {
        LocalDateTime now = now();
        SysPermissionEntity permission = new SysPermissionEntity();
        permission.setCode(code);
        permission.setName(name);
        permission.setDeleted(0);
        permission.setCreatedAt(now);
        permission.setUpdatedAt(now);
        permissionMapper.insert(permission);
        return permission.getId();
    }

    /**
     * 创建菜单。
     *
     * @param parentId 父菜单 ID，顶级传 0
     * @param name     菜单名称
     * @param routeKey 前端本地路由标识，目录传 {@code null}
     * @param sortNo   同级顺序
     * @return 菜单 ID
     */
    public Long createMenu(Long parentId, String name, String routeKey, int sortNo) {
        LocalDateTime now = now();
        SysMenuEntity menu = new SysMenuEntity();
        menu.setParentId(parentId);
        menu.setName(name);
        menu.setRouteKey(routeKey);
        menu.setSortNo(sortNo);
        menu.setDeleted(0);
        menu.setCreatedAt(now);
        menu.setUpdatedAt(now);
        menuMapper.insert(menu);
        return menu.getId();
    }

    /**
     * 给管理员授予角色。
     *
     * @param adminId 管理员 ID
     * @param roleId  角色 ID
     */
    public void grantRole(Long adminId, Long roleId) {
        adminRoleMapper.insertRelation(adminId, roleId, now(), null);
    }

    /**
     * 解除管理员的角色。
     *
     * @param adminId 管理员 ID
     * @param roleId  角色 ID
     */
    public void revokeRole(Long adminId, Long roleId) {
        adminRoleMapper.deleteRelation(adminId, roleId);
    }

    /**
     * 建立角色与权限关系。
     *
     * @param roleId       角色 ID
     * @param permissionId 权限 ID
     */
    public void linkRolePermission(Long roleId, Long permissionId) {
        jdbcTemplate.update("insert into sys_role_permission (role_id, permission_id, created_at, created_by) "
                + "values (?, ?, ?, null)", roleId, permissionId, now());
    }

    /**
     * 解除角色与权限关系。
     *
     * @param roleId       角色 ID
     * @param permissionId 权限 ID
     */
    public void unlinkRolePermission(Long roleId, Long permissionId) {
        jdbcTemplate.update("delete from sys_role_permission where role_id = ? and permission_id = ?", roleId,
                permissionId);
    }

    /**
     * 建立角色与菜单关系。
     *
     * @param roleId 角色 ID
     * @param menuId 菜单 ID
     */
    public void linkRoleMenu(Long roleId, Long menuId) {
        jdbcTemplate.update("insert into sys_role_menu (role_id, menu_id, created_at, created_by) "
                + "values (?, ?, ?, null)", roleId, menuId, now());
    }

    /**
     * 把管理员账号状态改为停用。
     *
     * @param adminId 管理员 ID
     */
    public void disableAdmin(Long adminId) {
        jdbcTemplate.update("update sys_admin set status = ? where id = ?", "disabled", adminId);
    }

    /**
     * 生成统一的 UTC 时间戳。
     *
     * @return 当前 UTC 时间
     */
    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
