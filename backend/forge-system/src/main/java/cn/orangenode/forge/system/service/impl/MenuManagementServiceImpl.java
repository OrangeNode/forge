package cn.orangenode.forge.system.service.impl;

import java.time.LocalDateTime;
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
import cn.orangenode.forge.system.request.MenuUpdateRequest;
import cn.orangenode.forge.system.response.MenuNodeResponse;
import cn.orangenode.forge.system.service.MenuManagementService;
import cn.orangenode.forge.system.support.PermissionCacheEvictor;
import cn.orangenode.forge.system.support.SystemCurrentAdmin;
import cn.orangenode.forge.system.support.SystemIds;
import cn.orangenode.forge.system.support.SystemTimes;
import cn.orangenode.forge.system.support.TextValues;

import lombok.extern.slf4j.Slf4j;

/**
 * 菜单管理用例实现。
 *
 * <p>菜单只描述前端可见性：{@code routeKey} 必须是前端本地路由白名单中的标识，
 * 后端不保存组件路径；路由标识非空时数据库唯一约束兜底唯一性，冲突由统一错误出口映射为 409。</p>
 *
 * <p>层级变更会校验父菜单存在、不能指向自身、也不能指向自己的后代；删除前检查子菜单与
 * 角色可见性引用。菜单可见性变更后使全部权限缓存立即失效，使授权调整在下一次请求生效。</p>
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
     * 构造菜单管理用例实现。
     *
     * @param menuMapper             菜单数据访问
     * @param permissionCacheEvictor 权限缓存失效入口
     * @param currentAdmin           当前操作者解析
     * @param converter              响应转换器
     */
    public MenuManagementServiceImpl(SysMenuMapper menuMapper, PermissionCacheEvictor permissionCacheEvictor,
            SystemCurrentAdmin currentAdmin, SystemConverter converter) {
        this.menuMapper = menuMapper;
        this.permissionCacheEvictor = permissionCacheEvictor;
        this.currentAdmin = currentAdmin;
        this.converter = converter;
    }

    /**
     * 查询完整菜单树。
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
     * 创建菜单节点。
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

        LocalDateTime now = SystemTimes.nowUtc();
        Long operator = currentAdmin.adminId();
        SysMenuEntity menu = new SysMenuEntity();
        menu.setParentId(parentId);
        menu.setName(request.name().trim());
        menu.setRouteKey(routeKey);
        menu.setSortNo(request.sortNo());
        menu.setDeleted(0);
        menu.setCreatedAt(now);
        menu.setUpdatedAt(now);
        menu.setCreatedBy(operator);
        menu.setUpdatedBy(operator);
        menuMapper.insert(menu);
        permissionCacheEvictor.evictAfterCommit();
        log.info("已创建菜单 [{}]，父菜单 {}", menu.getName(), parentId);
        return converter.toMenuNode(menu);
    }

    /**
     * 修改菜单节点。
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

        LocalDateTime now = SystemTimes.nowUtc();
        SysMenuEntity update = new SysMenuEntity();
        update.setId(menuId);
        update.setParentId(parentId);
        update.setName(request.name().trim());
        update.setRouteKey(routeKey);
        update.setSortNo(request.sortNo());
        update.setUpdatedAt(now);
        update.setUpdatedBy(currentAdmin.adminId());
        menuMapper.updateById(update);

        menu.setParentId(parentId);
        menu.setName(update.getName());
        menu.setRouteKey(routeKey);
        menu.setSortNo(update.getSortNo());
        menu.setUpdatedAt(now);
        permissionCacheEvictor.evictAfterCommit();
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
            throw new BusinessException(ErrorCode.CONFLICT, "该菜单已被角色授予可见性，请先解除授权再删除");
        }
        menuMapper.deleteById(menuId);
        permissionCacheEvictor.evictAfterCommit();
        log.info("已删除菜单 [{}]", menu.getName());
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
