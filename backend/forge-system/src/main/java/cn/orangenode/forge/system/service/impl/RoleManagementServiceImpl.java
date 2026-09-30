package cn.orangenode.forge.system.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.config.ForgeSecurityProperties;
import cn.orangenode.forge.framework.page.PageRequest;
import cn.orangenode.forge.framework.page.PageResponses;
import cn.orangenode.forge.system.converter.SystemConverter;
import cn.orangenode.forge.system.entity.SysMenuEntity;
import cn.orangenode.forge.system.entity.SysRoleEntity;
import cn.orangenode.forge.system.mapper.SysAdminRoleMapper;
import cn.orangenode.forge.system.mapper.SysMenuMapper;
import cn.orangenode.forge.system.mapper.SysRoleMapper;
import cn.orangenode.forge.system.request.RoleCreateRequest;
import cn.orangenode.forge.system.request.RoleGrantRequest;
import cn.orangenode.forge.system.request.RoleQuery;
import cn.orangenode.forge.system.request.RoleUpdateRequest;
import cn.orangenode.forge.system.response.RoleDetailResponse;
import cn.orangenode.forge.system.response.RoleOptionResponse;
import cn.orangenode.forge.system.service.RoleManagementService;
import cn.orangenode.forge.system.support.MenuPermissionCodes;
import cn.orangenode.forge.system.support.PermissionCacheEvictor;
import cn.orangenode.forge.system.support.SystemCurrentAdmin;
import cn.orangenode.forge.system.support.SystemIds;
import cn.orangenode.forge.system.support.SystemTimes;
import cn.orangenode.forge.system.support.TextValues;

import lombok.extern.slf4j.Slf4j;

/**
 * 角色管理用例实现。
 *
 * <p>角色代码保存前统一去空格并转为小写，与数据库不区分大小写的唯一键保持一致；
 * 唯一性由数据库约束兜底，冲突由统一错误出口映射为 409，不回显索引名。</p>
 *
 * <p>授权以菜单为单位：提交的菜单集合即角色最终可见的节点，接口权限由这些节点上声明的
 * {@code sys_menu.perm_codes} 推导，因此只有一张关系表需要全量替换，重复提交不会造成主键冲突。
 * 删除角色时先检查管理员引用并清理自身授权关系，不做静默级联；
 * 内置超级管理员角色按配置识别，不允许删除或修改代码。</p>
 */
@Slf4j
@Service
public class RoleManagementServiceImpl implements RoleManagementService {

    /**
     * 角色数据访问。
     */
    private final SysRoleMapper roleMapper;

    /**
     * 菜单数据访问，用于校验菜单授权是否存在及推导权限标识。
     */
    private final SysMenuMapper menuMapper;

    /**
     * 管理员与角色关系数据访问，用于删除前的引用检查。
     */
    private final SysAdminRoleMapper adminRoleMapper;

    /**
     * 认证与权限配置，提供超级管理员角色代码。
     */
    private final ForgeSecurityProperties securityProperties;

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
     * 构造角色管理用例实现。
     *
     * @param roleMapper             角色数据访问
     * @param menuMapper             菜单数据访问
     * @param adminRoleMapper        管理员与角色关系数据访问
     * @param securityProperties     认证与权限配置
     * @param permissionCacheEvictor 权限缓存失效入口
     * @param currentAdmin           当前操作者解析
     * @param converter              响应转换器
     */
    public RoleManagementServiceImpl(SysRoleMapper roleMapper, SysMenuMapper menuMapper,
            SysAdminRoleMapper adminRoleMapper, ForgeSecurityProperties securityProperties,
            PermissionCacheEvictor permissionCacheEvictor, SystemCurrentAdmin currentAdmin,
            SystemConverter converter) {
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.securityProperties = securityProperties;
        this.permissionCacheEvictor = permissionCacheEvictor;
        this.currentAdmin = currentAdmin;
        this.converter = converter;
    }

    /**
     * 分页查询角色，并按展示顺序稳定排序。
     *
     * @param pageRequest 分页入参
     * @param query       筛选条件，允许为 {@code null}
     * @return 角色分页结果
     */
    @Override
    public PageResponse<RoleDetailResponse> page(PageRequest pageRequest, RoleQuery query) {
        Page<SysRoleEntity> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        IPage<SysRoleEntity> result = roleMapper.selectPage(page, buildRoleQuery(query));
        return PageResponses.from(result, this::toSummaryDetail);
    }

    /**
     * 查询全部角色下拉选项。
     *
     * @return 角色下拉选项列表
     */
    @Override
    public List<RoleOptionResponse> options() {
        LambdaQueryWrapper<SysRoleEntity> wrapper = Wrappers.<SysRoleEntity>lambdaQuery()
                .orderByAsc(SysRoleEntity::getSortNo)
                .orderByAsc(SysRoleEntity::getId);
        return converter.toRoleOptions(roleMapper.selectList(wrapper));
    }

    /**
     * 查询角色详情，含已授予的菜单 ID 与由菜单推导出的权限标识。
     *
     * @param roleId 角色 ID
     * @return 角色详情
     */
    @Override
    public RoleDetailResponse detail(Long roleId) {
        SysRoleEntity role = requireRole(roleId);
        return toDetail(role);
    }

    /**
     * 创建角色，并按需写入初始菜单授权。
     *
     * @param request 创建入参
     * @return 创建后的角色详情
     */
    @Override
    @Transactional
    public RoleDetailResponse create(RoleCreateRequest request) {
        String code = normalizeCode(request.code());
        List<Long> menuIds = SystemIds.toLongList(request.menuIds(), "菜单 ID");
        requireMenus(menuIds);

        LocalDateTime now = SystemTimes.nowUtc();
        Long operator = currentAdmin.adminId();
        SysRoleEntity role = new SysRoleEntity();
        role.setCode(code);
        role.setName(request.name().trim());
        role.setDescription(TextValues.trimToNull(request.description()));
        role.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        role.setDeleted(0);
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        role.setCreatedBy(operator);
        role.setUpdatedBy(operator);
        roleMapper.insert(role);

        replaceMenus(role.getId(), menuIds, now, operator);
        permissionCacheEvictor.evictAfterCommit();
        log.info("已创建角色 [{}]，菜单 {} 个", code, menuIds.size());
        return toDetail(role);
    }

    /**
     * 修改角色基础信息。
     *
     * <p>内置超级管理员角色的代码不允许修改：该角色的权限与菜单由配置识别后直接给出，
     * 改代码会让内置语义与配置不再对应。</p>
     *
     * @param roleId  角色 ID
     * @param request 修改入参
     * @return 修改后的角色详情
     */
    @Override
    @Transactional
    public RoleDetailResponse update(Long roleId, RoleUpdateRequest request) {
        SysRoleEntity role = requireRole(roleId);
        String code = normalizeCode(request.code());
        if (isSuperRole(role) && !code.equals(role.getCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内置超级管理员角色的代码不允许修改");
        }
        LocalDateTime now = SystemTimes.nowUtc();
        SysRoleEntity update = new SysRoleEntity();
        update.setId(roleId);
        update.setCode(code);
        update.setName(request.name().trim());
        update.setDescription(TextValues.trimToNull(request.description()));
        update.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        update.setUpdatedAt(now);
        update.setUpdatedBy(currentAdmin.adminId());
        roleMapper.updateById(update);

        role.setCode(code);
        role.setName(update.getName());
        role.setDescription(update.getDescription());
        role.setSortNo(update.getSortNo());
        role.setUpdatedAt(now);
        permissionCacheEvictor.evictAfterCommit();
        return toDetail(role);
    }

    /**
     * 逻辑删除角色并清理其菜单授权关系。
     *
     * <p>被管理员引用时返回冲突；删除后角色代码仍占用唯一键，不会因为重复创建同一代码而复活历史数据。</p>
     *
     * @param roleId 角色 ID
     */
    @Override
    @Transactional
    public void delete(Long roleId) {
        SysRoleEntity role = requireRole(roleId);
        if (isSuperRole(role)) {
            throw new BusinessException(ErrorCode.CONFLICT, "内置超级管理员角色不允许删除");
        }
        if (adminRoleMapper.countByRoleId(roleId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该角色已分配给管理员，请先解除分配再删除");
        }
        roleMapper.deleteRoleMenus(roleId);
        roleMapper.deleteById(roleId);
        permissionCacheEvictor.evictAfterCommit();
        log.info("已删除角色 [{}]", role.getCode());
    }

    /**
     * 全量替换角色的菜单授权，接口权限随之变化。
     *
     * @param roleId  角色 ID
     * @param request 授权入参
     */
    @Override
    @Transactional
    public void replaceGrants(Long roleId, RoleGrantRequest request) {
        requireRole(roleId);
        List<Long> menuIds = SystemIds.toLongList(request.menuIds(), "菜单 ID");
        requireMenus(menuIds);

        replaceMenus(roleId, menuIds, SystemTimes.nowUtc(), currentAdmin.adminId());
        permissionCacheEvictor.evictAfterCommit();
        log.info("角色 [{}] 的授权已全量替换，菜单 {} 个，随之生效的权限标识 {} 个", roleId, menuIds.size(),
                collectPermissionCodes(menuIds).size());
    }

    /**
     * 构造角色分页查询条件。
     *
     * @param query 筛选条件，允许为 {@code null}
     * @return 类型安全的查询条件
     */
    private LambdaQueryWrapper<SysRoleEntity> buildRoleQuery(RoleQuery query) {
        LambdaQueryWrapper<SysRoleEntity> wrapper = Wrappers.<SysRoleEntity>lambdaQuery()
                .orderByAsc(SysRoleEntity::getSortNo)
                .orderByAsc(SysRoleEntity::getId);
        if (query != null && TextValues.hasText(query.name())) {
            wrapper.like(SysRoleEntity::getName, query.name().trim());
        }
        return wrapper;
    }

    /**
     * 查询角色实体，不存在时按资源不存在失败。
     *
     * @param roleId 角色 ID
     * @return 角色实体
     * @throws BusinessException 角色不存在或已删除时抛出 404
     */
    private SysRoleEntity requireRole(Long roleId) {
        SysRoleEntity role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在或已删除");
        }
        return role;
    }

    /**
     * 规范化角色代码。
     *
     * @param code 原始角色代码
     * @return 去空格并转为小写的角色代码
     */
    private String normalizeCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 判断角色是否为配置识别的内置超级管理员角色。
     *
     * @param role 角色实体
     * @return 是内置超级管理员角色时返回 {@code true}
     */
    private boolean isSuperRole(SysRoleEntity role) {
        String superRoleCode = securityProperties.getSuperRoleCode();
        return role.getCode() != null && superRoleCode != null && role.getCode().equalsIgnoreCase(superRoleCode);
    }

    /**
     * 校验菜单 ID 全部存在。
     *
     * @param menuIds 菜单 ID 列表，允许为空
     * @throws BusinessException 任一菜单不存在时抛出 400
     */
    private void requireMenus(List<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return;
        }
        List<SysMenuEntity> menus = menuMapper.selectByIds(menuIds);
        if (menus.size() != menuIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提交的菜单中包含不存在或已删除的菜单");
        }
    }

    /**
     * 重建角色的菜单关系。
     *
     * @param roleId    角色 ID
     * @param menuIds   菜单 ID 列表，已去重
     * @param now       关联创建时间（UTC）
     * @param operator  操作者管理员 ID
     */
    private void replaceMenus(Long roleId, List<Long> menuIds, LocalDateTime now, Long operator) {
        roleMapper.deleteRoleMenus(roleId);
        for (Long menuId : menuIds) {
            roleMapper.insertRoleMenu(roleId, menuId, now, operator);
        }
    }

    /**
     * 推导一批菜单节点声明的权限标识。
     *
     * @param menuIds 菜单 ID 列表，允许为空
     * @return 去重后的权限标识列表，入参为空时返回空列表
     */
    private List<String> collectPermissionCodes(List<Long> menuIds) {
        List<String> codes = new ArrayList<>();
        if (menuIds.isEmpty()) {
            return codes;
        }
        for (SysMenuEntity menu : menuMapper.selectByIds(menuIds)) {
            for (String code : MenuPermissionCodes.parse(menu.getPermCodes())) {
                if (!codes.contains(code)) {
                    codes.add(code);
                }
            }
        }
        return codes;
    }

    /**
     * 组装列表用的角色详情，不查询授权明细。
     *
     * <p>列表只展示角色基础信息：逐行查询菜单关系与权限标识会造成 N+1，
     * 授权明细由详情接口按角色单独返回。</p>
     *
     * @param role 角色实体
     * @return 角色详情，授权集合为空列表
     */
    private RoleDetailResponse toSummaryDetail(SysRoleEntity role) {
        return converter.toRoleDetail(role, List.of(), List.of(), isSuperRole(role));
    }

    /**
     * 组装带授权明细的角色详情。
     *
     * <p>权限标识由已授予菜单节点上的声明合并去重得到，不单独存储，
     * 因此角色详情展示的权限始终与菜单授权一致。</p>
     *
     * @param role 角色实体
     * @return 角色详情
     */
    private RoleDetailResponse toDetail(SysRoleEntity role) {
        if (role.getId() == null) {
            return converter.toRoleDetail(role, List.of(), List.of(), isSuperRole(role));
        }
        List<Long> menuIds = roleMapper.selectMenuIdsByRoleId(role.getId());
        List<String> permissionCodes = new ArrayList<>();
        for (String text : roleMapper.selectPermissionCodeTextsByRoleId(role.getId())) {
            for (String code : MenuPermissionCodes.parse(text)) {
                if (!permissionCodes.contains(code)) {
                    permissionCodes.add(code);
                }
            }
        }
        return converter.toRoleDetail(role, permissionCodes, menuIds, isSuperRole(role));
    }
}
