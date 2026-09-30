package cn.orangenode.forge.m3;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import cn.orangenode.forge.system.entity.SysAdminEntity;
import cn.orangenode.forge.system.entity.SysMenuEntity;
import cn.orangenode.forge.system.entity.SysRoleEntity;
import cn.orangenode.forge.system.mapper.SysAdminMapper;
import cn.orangenode.forge.system.mapper.SysAdminRoleMapper;
import cn.orangenode.forge.system.mapper.SysMenuMapper;
import cn.orangenode.forge.system.mapper.SysRoleMapper;

/**
 * M3 端到端验证的数据夹具。
 *
 * <p>账号、角色与菜单通过生产 Mapper 写入，密码同样按真实编码规则落库，
 * 因此登录用例验证的是完整凭据校验路径，而不是绕过编码的比较。</p>
 *
 * <p>接口权限不再有独立关系表：权限标识由菜单节点的 {@code perm_codes} 声明，
 * 因此夹具只提供“创建带权限标识的菜单”与“授予角色菜单”两个能力。</p>
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
     * @param adminMapper     管理员账号数据访问
     * @param roleMapper      角色数据访问
     * @param menuMapper      菜单数据访问
     * @param adminRoleMapper 管理员与角色关系数据访问
     * @param passwordEncoder 密码编码器
     * @param jdbcTemplate    关系表使用的 JDBC 模板
     */
    public M3FixtureService(SysAdminMapper adminMapper, SysRoleMapper roleMapper, SysMenuMapper menuMapper,
            SysAdminRoleMapper adminRoleMapper, PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
        this.adminMapper = adminMapper;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
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
     * 创建菜单，并声明该节点对应的接口权限标识。
     *
     * <p>权限标识挂在菜单节点上：角色授予该节点即获得这些权限，
     * 因此用例通过本方法同时准备“可见菜单”与“接口权限”。</p>
     *
     * @param parentId  父菜单 ID，顶级传 0
     * @param name      菜单名称
     * @param routeKey  前端本地路由标识，目录传 {@code null}
     * @param permCodes 该节点声明的权限标识，没有权限时传 {@code null}
     * @param sortNo    同级顺序
     * @return 菜单 ID
     */
    public Long createMenuWithPermissions(Long parentId, String name, String routeKey, String permCodes, int sortNo) {
        LocalDateTime now = now();
        SysMenuEntity menu = new SysMenuEntity();
        menu.setParentId(parentId);
        menu.setName(name);
        menu.setRouteKey(routeKey);
        menu.setPermCodes(permCodes);
        menu.setSortNo(sortNo);
        menu.setDeleted(0);
        menu.setCreatedAt(now);
        menu.setUpdatedAt(now);
        menuMapper.insert(menu);
        return menu.getId();
    }

    /**
     * 创建不声明权限的菜单。
     *
     * @param parentId 父菜单 ID，顶级传 0
     * @param name     菜单名称
     * @param routeKey 前端本地路由标识，目录传 {@code null}
     * @param sortNo   同级顺序
     * @return 菜单 ID
     */
    public Long createMenu(Long parentId, String name, String routeKey, int sortNo) {
        return createMenuWithPermissions(parentId, name, routeKey, null, sortNo);
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
     * 解除角色与菜单关系。
     *
     * @param roleId 角色 ID
     * @param menuId 菜单 ID
     */
    public void unlinkRoleMenu(Long roleId, Long menuId) {
        jdbcTemplate.update("delete from sys_role_menu where role_id = ? and menu_id = ?", roleId, menuId);
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
