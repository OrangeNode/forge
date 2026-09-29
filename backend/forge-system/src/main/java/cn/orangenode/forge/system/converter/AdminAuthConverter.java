package cn.orangenode.forge.system.converter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.framework.security.AdminToken;
import cn.orangenode.forge.system.entity.SysAdminEntity;
import cn.orangenode.forge.system.entity.SysMenuEntity;
import cn.orangenode.forge.system.response.AdminLoginResponse;
import cn.orangenode.forge.system.response.AdminMenuResponse;
import cn.orangenode.forge.system.response.AdminProfileResponse;
import cn.orangenode.forge.system.response.AdminSummaryResponse;

/**
 * 管理员认证相关转换器。
 *
 * <p>Entity、Request、Response 分离，转换逐字段显式赋值：不使用反射批量复制，
 * 避免将来新增字段时把密码编码结果之类的内部字段意外带出。</p>
 */
@Component
public class AdminAuthConverter {

    /**
     * 令牌方案，属于接口契约的一部分，不随配置变化。
     */
    private static final String TOKEN_TYPE = "Bearer";

    /**
     * 把管理员实体转换为基础信息响应。
     *
     * @param entity 管理员实体
     * @return 基础信息响应
     */
    public AdminSummaryResponse toSummary(SysAdminEntity entity) {
        return new AdminSummaryResponse(String.valueOf(entity.getId()), entity.getUsername(),
                entity.getDisplayName());
    }

    /**
     * 组装登录响应。
     *
     * @param token  已签发的令牌
     * @param entity 管理员实体
     * @return 登录响应
     */
    public AdminLoginResponse toLoginResponse(AdminToken token, SysAdminEntity entity) {
        return new AdminLoginResponse(token.value(), TOKEN_TYPE, token.expiresInSeconds(), toSummary(entity));
    }

    /**
     * 组装当前管理员身份与权限响应。
     *
     * @param entity          管理员实体
     * @param permissionCodes 当前有效权限代码
     * @return 身份与权限响应
     */
    public AdminProfileResponse toProfileResponse(SysAdminEntity entity, List<String> permissionCodes) {
        return new AdminProfileResponse(String.valueOf(entity.getId()), entity.getUsername(),
                entity.getDisplayName(), List.copyOf(permissionCodes));
    }

    /**
     * 把可见菜单列表组装为菜单树。
     *
     * <p>只从顶级菜单向下遍历：父菜单不可见时其子菜单也不会出现，避免下发无法到达的孤立节点。</p>
     *
     * @param menus 管理员可见的菜单列表
     * @return 菜单树，没有可见菜单时返回空列表
     */
    public List<AdminMenuResponse> toMenuTree(List<SysMenuEntity> menus) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        Map<Long, List<SysMenuEntity>> childrenByParent = menus.stream()
                .collect(Collectors.groupingBy(menu -> menu.getParentId() == null ? 0L : menu.getParentId()));
        return toMenuNodes(childrenByParent, 0L);
    }

    /**
     * 递归构建菜单节点。
     *
     * @param childrenByParent 按父菜单 ID 分组的菜单
     * @param parentId         当前层级的父菜单 ID
     * @return 当前层级的菜单节点列表
     */
    private List<AdminMenuResponse> toMenuNodes(Map<Long, List<SysMenuEntity>> childrenByParent, Long parentId) {
        List<SysMenuEntity> siblings = childrenByParent.get(parentId);
        if (siblings == null || siblings.isEmpty()) {
            return List.of();
        }
        return siblings.stream()
                .map(menu -> new AdminMenuResponse(String.valueOf(menu.getId()), menu.getName(), menu.getRouteKey(),
                        toMenuNodes(childrenByParent, menu.getId())))
                .toList();
    }
}
