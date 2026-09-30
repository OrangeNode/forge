package cn.orangenode.forge.system.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.system.converter.SystemConverter;
import cn.orangenode.forge.system.entity.SysMenuEntity;
import cn.orangenode.forge.system.mapper.SysMenuMapper;
import cn.orangenode.forge.system.request.MenuCreateRequest;
import cn.orangenode.forge.system.request.MenuPermissionRequest;
import cn.orangenode.forge.system.request.MenuUpdateRequest;
import cn.orangenode.forge.system.response.MenuNodeResponse;
import cn.orangenode.forge.system.response.MenuPermissionResponse;
import cn.orangenode.forge.system.service.MenuManagementService;
import cn.orangenode.forge.system.support.MenuPermissionCodes;
import cn.orangenode.forge.system.support.MenuPermissionValues;
import cn.orangenode.forge.system.support.PermissionCacheEvictor;
import cn.orangenode.forge.system.support.PermissionCodeFormat;
import cn.orangenode.forge.system.support.SystemCurrentAdmin;
import cn.orangenode.forge.system.support.SystemIds;
import cn.orangenode.forge.system.support.SystemTimes;
import cn.orangenode.forge.system.support.TextValues;

import lombok.extern.slf4j.Slf4j;

/**
 * 菜单管理用例实现。
 *
 * <p>菜单是本项目 RBAC 的唯一载体：既描述前端可见性（{@code routeKey} 必须是前端本地路由白名单中的标识，
 * 后端不保存组件路径），也声明该节点对应的接口权限标识（{@code permCodes}）。
 * 角色授予菜单节点即同时获得该节点声明的接口权限，因此菜单页面就是权限维护入口。</p>
 *
 * <p>权限标识在全局范围内唯一：同一个代码被两个节点声明时，角色授予哪一个都能拿到该权限，
 * 权限来源会变得无法解释。因此新增与修改前检查占用，重复直接失败。</p>
 *
 * <p>权限代码、中文名称与说明共同保存在菜单行中，页面新增权限时一次录入完整资料，
 * 不再依赖独立权限表，也不会出现新增权限只有代码、中文名称无处填写的问题。</p>
 *
 * <p>以上任何变更提交后都会使全部权限缓存立即失效，使授权调整在下一次请求生效。</p>
 */
@Slf4j
@Service
public class MenuManagementServiceImpl implements MenuManagementService {

    /**
     * 顶级菜单的父菜单 ID 约定值。
     */
    private static final Long ROOT_PARENT_ID = 0L;

    /**
     * 顶级菜单父 ID 的对外文本形式。
     */
    private static final String ROOT_PARENT_ID_TEXT = "0";

    /**
     * 权限标识列的长度上限，与 {@code sys_menu.perm_codes} 的列宽一致。
     *
     * <p>请求体逐项校验只能约束单个代码，拼接后的整体长度在保存前判断，
     * 避免超长内容被数据库截断后变成不可解释的权限。</p>
     */
    private static final int PERMISSION_CODES_MAX_LENGTH = 512;

    /**
     * 菜单数据访问。
     */
    private final SysMenuMapper menuMapper;

    /**
     * 权限缓存失效入口。
     */
    private final PermissionCacheEvictor permissionCacheEvictor;

    /**
     * 当前操作者解析，用于写入审计字段。
     */
    private final SystemCurrentAdmin currentAdmin;

    /**
     * 响应转换器。
     */
    private final SystemConverter converter;

    /**
     * 菜单权限 JSON 列转换器。
     */
    private final MenuPermissionValues permissionValues;

    /**
     * 构造菜单管理用例实现。
     *
     * @param menuMapper             菜单数据访问
     * @param permissionCacheEvictor 权限缓存失效入口
     * @param currentAdmin           当前操作者解析
     * @param converter              响应转换器
     * @param permissionValues       菜单权限 JSON 列转换器
     */
    public MenuManagementServiceImpl(SysMenuMapper menuMapper, PermissionCacheEvictor permissionCacheEvictor,
            SystemCurrentAdmin currentAdmin, SystemConverter converter, MenuPermissionValues permissionValues) {
        this.menuMapper = menuMapper;
        this.permissionCacheEvictor = permissionCacheEvictor;
        this.currentAdmin = currentAdmin;
        this.converter = converter;
        this.permissionValues = permissionValues;
    }

    /**
     * 查询完整菜单树，含每个节点声明的接口权限。
     *
     * @return 菜单树
     */
    @Override
    public List<MenuNodeResponse> tree() {
        LambdaQueryWrapper<SysMenuEntity> wrapper = Wrappers.<SysMenuEntity>lambdaQuery()
                .orderByAsc(SysMenuEntity::getSortNo)
                .orderByAsc(SysMenuEntity::getId);
        return converter.toMenuTree(menuMapper.selectList(wrapper));
    }

    /**
     * 创建菜单节点，并同步该节点声明的权限标识与展示资料。
     *
     * @param request 创建入参
     * @return 创建后的菜单节点
     */
    @Override
    @Transactional
    public MenuNodeResponse create(MenuCreateRequest request) {
        Long parentId = resolveParentId(request.parentId());
        requireParentExists(parentId);
        String routeKey = TextValues.trimToNull(request.routeKey());
        requireRouteKeyAvailable(routeKey, null);
        List<MenuPermissionResponse> permissions = resolvePermissions(request.permissions(), request.permCodes(), null);
        List<String> permCodes = permissions.stream().map(MenuPermissionResponse::code).toList();

        LocalDateTime now = SystemTimes.nowUtc();
        Long operator = currentAdmin.adminId();
        SysMenuEntity menu = new SysMenuEntity();
        menu.setParentId(parentId);
        menu.setName(request.name().trim());
        menu.setMenuType(resolveMenuType(request.menuType(), routeKey));
        menu.setIcon(TextValues.trimToNull(request.icon()));
        menu.setRouteKey(routeKey);
        menu.setPermCodes(MenuPermissionCodes.join(permCodes));
        menu.setPermissionsJson(permissionValues.serialize(permissions));
        menu.setSortNo(request.sortNo());
        menu.setDeleted(0);
        menu.setCreatedAt(now);
        menu.setUpdatedAt(now);
        menu.setCreatedBy(operator);
        menu.setUpdatedBy(operator);
        menuMapper.insert(menu);
        permissionCacheEvictor.evictAfterCommit();
        log.info("已创建菜单 [{}]，父菜单 {}，权限标识 {} 个", menu.getName(), parentId, permCodes.size());
        return converter.toMenuNode(menu);
    }

    /**
     * 修改菜单节点，并同步该节点声明的权限标识与展示资料。
     *
     * @param menuId  菜单 ID
     * @param request 修改入参
     * @return 修改后的菜单节点
     */
    @Override
    @Transactional
    public MenuNodeResponse update(Long menuId, MenuUpdateRequest request) {
        SysMenuEntity menu = requireMenu(menuId);
        Long parentId = resolveParentId(request.parentId());
        requireParentExists(parentId);
        requireNoCycle(menuId, parentId);
        String routeKey = TextValues.trimToNull(request.routeKey());
        requireRouteKeyAvailable(routeKey, menuId);
        List<MenuPermissionResponse> permissions = resolvePermissions(request.permissions(), request.permCodes(), menuId);
        List<String> permCodes = permissions.stream().map(MenuPermissionResponse::code).toList();

        LocalDateTime now = SystemTimes.nowUtc();
        Long operator = currentAdmin.adminId();
        String permCodeText = MenuPermissionCodes.join(permCodes);
        SysMenuEntity update = new SysMenuEntity();
        update.setId(menuId);
        update.setParentId(parentId);
        update.setName(request.name().trim());
        update.setMenuType(resolveMenuType(request.menuType(), routeKey));
        update.setIcon(TextValues.trimToNull(request.icon()));
        update.setRouteKey(routeKey);
        update.setSortNo(request.sortNo());
        update.setUpdatedAt(now);
        update.setUpdatedBy(operator);
        menuMapper.updateById(update);
        // 权限标识可能被清空：updateById 会跳过 null 字段，这里用显式 set 才能把列写回 NULL
        menuMapper.update(null, Wrappers.<SysMenuEntity>lambdaUpdate()
                .eq(SysMenuEntity::getId, menuId)
                .set(SysMenuEntity::getPermCodes, permCodeText)
                .set(SysMenuEntity::getPermissionsJson, permissionValues.serialize(permissions)));

        menu.setParentId(parentId);
        menu.setName(update.getName());
        menu.setMenuType(update.getMenuType());
        menu.setIcon(update.getIcon());
        menu.setRouteKey(routeKey);
        menu.setPermCodes(permCodeText);
        menu.setPermissionsJson(permissionValues.serialize(permissions));
        menu.setSortNo(update.getSortNo());
        menu.setUpdatedAt(now);
        permissionCacheEvictor.evictAfterCommit();
        log.info("已修改菜单 [{}]，权限标识 {} 个", menu.getName(), permCodes.size());
        return converter.toMenuNode(menu);
    }

    /**
     * 逻辑删除菜单节点。
     *
     * @param menuId 菜单 ID
     */
    @Override
    @Transactional
    public void delete(Long menuId) {
        SysMenuEntity menu = requireMenu(menuId);
        if (menuMapper.countChildren(menuId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该菜单存在子菜单，请先删除子菜单");
        }
        if (menuMapper.countRoleReferences(menuId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该菜单已被角色授予，请先解除授权再删除");
        }
        menuMapper.deleteById(menuId);
        permissionCacheEvictor.evictAfterCommit();
        log.info("已删除菜单 [{}]，其声明的权限标识不再生效", menu.getName());
    }

    /**
     * 校验并规范化本次提交的权限标识。
     *
     * <p>三重校验：格式必须符合 {@code 模块:资源:动作}，请求内不得重复，且不得被其他菜单占用。
     * 校验通过后返回去重且保持提交顺序的列表。</p>
     *
     * @param submitted     新客户端提交的完整权限资料，允许为空
     * @param legacyCodes   旧客户端提交的权限代码，允许为空
     * @param excludeMenuId 修改时排除自身，创建时传 {@code null}
     * @return 规范化后的权限资料列表
     * @throws BusinessException 格式非法、重复或已被占用时抛出 400 或 409
     */
    private List<MenuPermissionResponse> resolvePermissions(List<MenuPermissionRequest> submitted,
            List<String> legacyCodes, Long excludeMenuId) {
        List<MenuPermissionResponse> permissions = new ArrayList<>();
        if (submitted != null && !submitted.isEmpty()) {
            for (MenuPermissionRequest item : submitted) {
                String code = PermissionCodeFormat.normalize(item.code());
                if (code == null) {
                    continue;
                }
                if (!PermissionCodeFormat.isValid(code)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "权限标识 [" + code + "] 格式非法：" + PermissionCodeFormat.DESCRIPTION);
                }
                if (permissions.stream().anyMatch(existing -> existing.code().equals(code))) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "权限标识 [" + code + "] 在同一菜单中重复提交");
                }
                permissions.add(new MenuPermissionResponse(code, item.name().strip(),
                        TextValues.trimToNull(item.description()), null, null));
            }
        } else if (legacyCodes != null) {
            for (String rawCode : legacyCodes) {
                String code = PermissionCodeFormat.normalize(rawCode);
                if (code != null) {
                    permissions.add(new MenuPermissionResponse(code, code, null, null, null));
                }
            }
        }
        List<String> codes = permissions.stream().map(MenuPermissionResponse::code).toList();
        String text = MenuPermissionCodes.join(codes);
        if (text != null && text.length() > PERMISSION_CODES_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "权限标识总长度不能超过 " + PERMISSION_CODES_MAX_LENGTH + " 个字符");
        }
        requirePermissionCodesAvailable(codes, excludeMenuId);
        return List.copyOf(permissions);
    }

    /**
     * 校验权限标识未被其他菜单占用。
     *
     * <p>已逻辑删除的菜单仍占用标识：重新启用同名权限码的意义不明确，直接要求使用新标识，
     * 与路由标识的处理一致。</p>
     *
     * @param codes         本次提交的权限标识
     * @param excludeMenuId 需要排除的菜单 ID，允许为 {@code null}
     * @throws BusinessException 标识已被其他菜单占用时抛出 409
     */
    private void requirePermissionCodesAvailable(List<String> codes, Long excludeMenuId) {
        if (codes.isEmpty()) {
            return;
        }
        List<String> occupied = new ArrayList<>();
        for (String text : menuMapper.selectPermissionCodeTextsExcept(excludeMenuId)) {
            occupied.addAll(MenuPermissionCodes.parse(text));
        }
        for (String code : codes) {
            if (occupied.contains(code)) {
                throw new BusinessException(ErrorCode.CONFLICT, "权限标识 [" + code + "] 已被其他菜单使用");
            }
        }
    }

    /**
     * 解析菜单类型；旧客户端未提交时按是否存在路由标识推导。
     *
     * @param submitted 客户端提交值，允许为空
     * @param routeKey  路由标识，允许为空
     * @return directory 或 page
     */
    private String resolveMenuType(String submitted, String routeKey) {
        String value = TextValues.trimToNull(submitted);
        return value == null ? (routeKey == null ? "directory" : "page") : value;
    }

    /**
     * 解析父菜单 ID。
     *
     * <p>空值与约定值 {@code 0} 都按顶级菜单处理：{@code 0} 不是合法的主键，
     * 不能走通用的正整数校验，否则“新建顶级菜单”会被当成参数错误拒绝。</p>
     *
     * @param rawParentId 对外父菜单 ID，允许为空
     * @return 父菜单 ID，空值或 {@code 0} 按顶级菜单处理
     * @throws BusinessException ID 格式非法时抛出 400
     */
    private Long resolveParentId(String rawParentId) {
        if (!TextValues.hasText(rawParentId)) {
            return ROOT_PARENT_ID;
        }
        String text = rawParentId.strip();
        if (ROOT_PARENT_ID_TEXT.equals(text)) {
            return ROOT_PARENT_ID;
        }
        return SystemIds.toLong(text, "父菜单 ID");
    }

    /**
     * 校验父菜单存在且未删除。
     *
     * @param parentId 父菜单 ID
     * @throws BusinessException 父菜单不存在时抛出 400
     */
    private void requireParentExists(Long parentId) {
        if (ROOT_PARENT_ID.equals(parentId)) {
            return;
        }
        if (menuMapper.selectById(parentId) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "父菜单不存在或已删除");
        }
    }

    /**
     * 校验父菜单不会形成环。
     *
     * <p>沿新的父菜单向上追溯祖先：如果遇到自身，说明新父菜单是自己的后代，会把菜单树切成环。
     * 已存在的历史环不会无限循环——追溯步数受当前菜单总量限制。</p>
     *
     * @param menuId   被修改的菜单 ID
     * @param parentId 新的父菜单 ID
     * @throws BusinessException 父菜单为自身或自身后代时抛出 400
     */
    private void requireNoCycle(Long menuId, Long parentId) {
        if (ROOT_PARENT_ID.equals(parentId)) {
            return;
        }
        if (menuId.equals(parentId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "父菜单不能是菜单自身");
        }
        Map<Long, Long> parentById = loadParentIds();
        Long cursor = parentId;
        int guard = parentById.size() + 1;
        while (cursor != null && !ROOT_PARENT_ID.equals(cursor) && guard-- > 0) {
            if (menuId.equals(cursor)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "父菜单不能是当前菜单的子菜单");
            }
            cursor = parentById.get(cursor);
        }
    }

    /**
     * 读取全部有效菜单的父子关系。
     *
     * @return 菜单 ID 到父菜单 ID 的映射
     */
    private Map<Long, Long> loadParentIds() {
        Map<Long, Long> parentById = new HashMap<>();
        for (SysMenuEntity menu : menuMapper.selectAllMenus()) {
            parentById.put(menu.getId(), menu.getParentId());
        }
        return parentById;
    }

    /**
     * 校验路由标识未被其他菜单占用。
     *
     * <p>目录节点没有路由标识，不参与唯一性判断；数据库唯一约束兜底并发创建，
     * 这里的检查只用于给出更清楚的中文提示。已逻辑删除的菜单仍占用标识，
     * 因此查询不过滤 {@code deleted}。</p>
     *
     * @param routeKey      路由标识，允许为空
     * @param excludeMenuId 需要排除的菜单 ID（修改自身时使用），允许为空
     * @throws BusinessException 标识已被占用时抛出 409
     */
    private void requireRouteKeyAvailable(String routeKey, Long excludeMenuId) {
        if (!TextValues.hasText(routeKey)) {
            return;
        }
        if (menuMapper.countByRouteKey(routeKey, excludeMenuId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "路由标识已被其他菜单使用");
        }
    }

    /**
     * 查询菜单实体，不存在时按资源不存在失败。
     *
     * @param menuId 菜单 ID
     * @return 菜单实体
     * @throws BusinessException 菜单不存在或已删除时抛出 404
     */
    private SysMenuEntity requireMenu(Long menuId) {
        SysMenuEntity menu = menuMapper.selectById(menuId);
        if (menu == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "菜单不存在或已删除");
        }
        return menu;
    }
}
