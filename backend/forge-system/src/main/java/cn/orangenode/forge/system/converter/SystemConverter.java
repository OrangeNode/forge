package cn.orangenode.forge.system.converter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.system.entity.SysAdminEntity;
import cn.orangenode.forge.system.entity.SysMenuEntity;
import cn.orangenode.forge.system.entity.SysPermissionEntity;
import cn.orangenode.forge.system.entity.SysRoleEntity;
import cn.orangenode.forge.system.response.AdminDetailResponse;
import cn.orangenode.forge.system.response.MenuNodeResponse;
import cn.orangenode.forge.system.response.PermissionResponse;
import cn.orangenode.forge.system.response.RoleDetailResponse;
import cn.orangenode.forge.system.response.RoleOptionResponse;
import cn.orangenode.forge.system.support.SystemIds;
import cn.orangenode.forge.system.support.SystemTimes;

/**
 * 系统管理响应转换器。
 *
 * <p>Entity、Request、Response 分离，转换逐字段显式赋值：不使用反射批量复制，
 * 避免将来新增字段时把密码编码结果之类的内部字段意外带出。
 * 对外 ID 一律字符串，时间按 UTC 语义输出为带时区的时刻。</p>
 */
@Component
public class SystemConverter {

    /**
     * 顶级菜单的父菜单 ID 约定值。
     */
    private static final Long ROOT_PARENT_ID = 0L;

    /**
     * 把管理员实体转换为详情响应。
     *
     * @param entity 管理员实体
     * @param roles  当前拥有的角色摘要，允许为空
     * @return 管理员详情响应
     */
    public AdminDetailResponse toAdminDetail(SysAdminEntity entity, List<RoleOptionResponse> roles) {
        return new AdminDetailResponse(SystemIds.toText(entity.getId()), entity.getUsername(), entity.getDisplayName(),
                entity.getStatus(), List.copyOf(roles), SystemTimes.toInstant(entity.getLastLoginAt()),
                SystemTimes.toInstant(entity.getCreatedAt()), SystemTimes.toInstant(entity.getUpdatedAt()));
    }

    /**
     * 把角色实体转换为下拉选项。
     *
     * @param entity 角色实体
     * @return 角色下拉选项
     */
    public RoleOptionResponse toRoleOption(SysRoleEntity entity) {
        return new RoleOptionResponse(SystemIds.toText(entity.getId()), entity.getCode(), entity.getName());
    }

    /**
     * 批量转换角色下拉选项。
     *
     * @param roles 角色实体列表，允许为空
     * @return 角色下拉选项列表，入参为空时返回空列表
     */
    public List<RoleOptionResponse> toRoleOptions(List<SysRoleEntity> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream().map(this::toRoleOption).toList();
    }

    /**
     * 把角色实体转换为详情响应。
     *
     * @param entity        角色实体
     * @param permissionIds 已授予的权限 ID
     * @param menuIds       已授予的菜单 ID
     * @param superRole     是否为内置超级管理员角色
     * @return 角色详情响应
     */
    public RoleDetailResponse toRoleDetail(SysRoleEntity entity, List<Long> permissionIds, List<Long> menuIds,
            boolean superRole) {
        return new RoleDetailResponse(SystemIds.toText(entity.getId()), entity.getCode(), entity.getName(),
                entity.getDescription(), entity.getSortNo(), superRole, toIdTexts(permissionIds), toIdTexts(menuIds),
                SystemTimes.toInstant(entity.getCreatedAt()), SystemTimes.toInstant(entity.getUpdatedAt()));
    }

    /**
     * 把权限实体转换为权限信息。
     *
     * @param entity 权限实体
     * @return 权限信息
     */
    public PermissionResponse toPermission(SysPermissionEntity entity) {
        return new PermissionResponse(SystemIds.toText(entity.getId()), entity.getCode(), entity.getName(),
                entity.getDescription(), SystemTimes.toInstant(entity.getCreatedAt()),
                SystemTimes.toInstant(entity.getUpdatedAt()));
    }

    /**
     * 把单个菜单实体转换为菜单节点。
     *
     * <p>用于新增与修改的响应：只回传该节点本身，子节点由菜单树接口按完整层级返回。
     * 父菜单 ID 为空时按顶级菜单约定输出 {@code 0}。</p>
     *
     * @param entity 菜单实体
     * @return 菜单节点，不含子菜单
     */
    public MenuNodeResponse toMenuNode(SysMenuEntity entity) {
        return new MenuNodeResponse(SystemIds.toText(entity.getId()),
                SystemIds.toText(entity.getParentId() == null ? ROOT_PARENT_ID : entity.getParentId()),
                entity.getName(), entity.getRouteKey(), entity.getSortNo(), List.of());
    }

    /**
     * 把菜单列表组装为菜单树。
     *
     * <p>只从顶级菜单向下遍历：父菜单不可见时其子菜单也不会出现，避免下发无法到达的孤立节点；
     * 同时用已访问集合阻断环，即使历史数据存在环也不会无限递归。</p>
     *
     * @param menus 菜单列表，允许为空
     * @return 菜单树，没有菜单时返回空列表
     */
    public List<MenuNodeResponse> toMenuTree(List<SysMenuEntity> menus) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        Map<Long, List<SysMenuEntity>> childrenByParent = menus.stream()
                .collect(Collectors.groupingBy(menu -> menu.getParentId() == null ? ROOT_PARENT_ID : menu.getParentId()));
        return toMenuNodes(childrenByParent, ROOT_PARENT_ID, new HashSet<>());
    }

    /**
     * 递归构建菜单节点。
     *
     * @param childrenByParent 按父菜单 ID 分组的菜单
     * @param parentId         当前层级的父菜单 ID
     * @param visited          已展开的菜单 ID，用于阻断异常数据形成的环
     * @return 当前层级的菜单节点列表
     */
    private List<MenuNodeResponse> toMenuNodes(Map<Long, List<SysMenuEntity>> childrenByParent, Long parentId,
            Set<Long> visited) {
        List<SysMenuEntity> siblings = childrenByParent.get(parentId);
        if (siblings == null || siblings.isEmpty()) {
            return List.of();
        }
        List<MenuNodeResponse> nodes = new ArrayList<>(siblings.size());
        for (SysMenuEntity menu : siblings) {
            if (!visited.add(menu.getId())) {
                continue;
            }
            nodes.add(new MenuNodeResponse(SystemIds.toText(menu.getId()), SystemIds.toText(menu.getParentId()),
                    menu.getName(), menu.getRouteKey(), menu.getSortNo(),
                    toMenuNodes(childrenByParent, menu.getId(), visited)));
        }
        return List.copyOf(nodes);
    }

    /**
     * 把业务层 ID 集合转换为对外字符串集合。
     *
     * @param ids 业务层 ID 集合，允许为空
     * @return 字符串 ID 列表，入参为空时返回空列表
     */
    private List<String> toIdTexts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().map(SystemIds::toText).toList();
    }
}
