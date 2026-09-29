package cn.orangenode.forge.system.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.page.PageRequest;
import cn.orangenode.forge.framework.page.PageResponses;
import cn.orangenode.forge.framework.security.AdminTokenService;
import cn.orangenode.forge.system.converter.SystemConverter;
import cn.orangenode.forge.system.credential.AdminCredentialPolicy;
import cn.orangenode.forge.system.entity.SysAdminEntity;
import cn.orangenode.forge.system.entity.SysRoleEntity;
import cn.orangenode.forge.system.enums.AdminAccountStatus;
import cn.orangenode.forge.system.mapper.SysAdminMapper;
import cn.orangenode.forge.system.mapper.SysAdminRoleMapper;
import cn.orangenode.forge.system.mapper.SysRoleMapper;
import cn.orangenode.forge.system.request.AdminCreateRequest;
import cn.orangenode.forge.system.request.AdminPasswordResetRequest;
import cn.orangenode.forge.system.request.AdminQuery;
import cn.orangenode.forge.system.request.AdminRoleAssignRequest;
import cn.orangenode.forge.system.request.AdminStatusRequest;
import cn.orangenode.forge.system.request.AdminUpdateRequest;
import cn.orangenode.forge.system.response.AdminDetailResponse;
import cn.orangenode.forge.system.service.AdminManagementService;
import cn.orangenode.forge.system.support.PermissionCacheEvictor;
import cn.orangenode.forge.system.support.SystemCurrentAdmin;
import cn.orangenode.forge.system.support.SystemIds;
import cn.orangenode.forge.system.support.SystemTimes;
import cn.orangenode.forge.system.support.TextValues;

import lombok.extern.slf4j.Slf4j;

/**
 * 管理员管理用例实现。
 *
 * <p>账号状态、创建者与时间由服务端决定；用户名按证书策略规范化后保存，密码只保存编码结果。
 * 状态变更使用条件更新并把“受影响行数为 0”映射为冲突，避免并发下两个请求都以为自己成功。</p>
 *
 * <p>停用与重置密码调用令牌会话的按账号撤销：既有令牌在下一次请求即失效；
 * 角色关系变更在数据库提交后递增全局权限版本，使权限缓存立即失效。</p>
 */
@Slf4j
@Service
public class AdminManagementServiceImpl implements AdminManagementService {

    /**
     * 管理员账号数据访问。
     */
    private final SysAdminMapper adminMapper;

    /**
     * 角色数据访问。
     */
    private final SysRoleMapper roleMapper;

    /**
     * 管理员与角色关系数据访问。
     */
    private final SysAdminRoleMapper adminRoleMapper;

    /**
     * 密码编码器，只保存编码结果。
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * 令牌会话，用于停用与改密后的批量撤销。
     */
    private final AdminTokenService tokenService;

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
     * 构造管理员管理用例实现。
     *
     * @param adminMapper            管理员账号数据访问
     * @param roleMapper             角色数据访问
     * @param adminRoleMapper        管理员与角色关系数据访问
     * @param passwordEncoder        密码编码器
     * @param tokenService           令牌会话
     * @param permissionCacheEvictor 权限缓存失效入口
     * @param currentAdmin           当前操作者解析
     * @param converter              响应转换器
     */
    public AdminManagementServiceImpl(SysAdminMapper adminMapper, SysRoleMapper roleMapper,
            SysAdminRoleMapper adminRoleMapper, PasswordEncoder passwordEncoder, AdminTokenService tokenService,
            PermissionCacheEvictor permissionCacheEvictor, SystemCurrentAdmin currentAdmin,
            SystemConverter converter) {
        this.adminMapper = adminMapper;
        this.roleMapper = roleMapper;
        this.adminRoleMapper = adminRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.permissionCacheEvictor = permissionCacheEvictor;
        this.currentAdmin = currentAdmin;
        this.converter = converter;
    }

    /**
     * 分页查询管理员，并为当前页批量补充角色摘要。
     *
     * @param pageRequest 分页入参
     * @param query       筛选条件，允许为 {@code null}
     * @return 管理员分页结果
     */
    @Override
    public PageResponse<AdminDetailResponse> page(PageRequest pageRequest, AdminQuery query) {
        Page<SysAdminEntity> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        IPage<SysAdminEntity> result = adminMapper.selectPage(page, buildAdminQuery(query));
        Map<Long, List<SysRoleEntity>> rolesByAdmin = loadRolesByAdmin(
                result.getRecords().stream().map(SysAdminEntity::getId).toList());
        return PageResponses.from(result, entity -> converter.toAdminDetail(entity,
                converter.toRoleOptions(rolesByAdmin.getOrDefault(entity.getId(), List.of()))));
    }

    /**
     * 查询管理员详情，含当前角色摘要。
     *
     * @param adminId 管理员 ID
     * @return 管理员详情
     */
    @Override
    public AdminDetailResponse detail(Long adminId) {
        SysAdminEntity admin = requireAdmin(adminId);
        return converter.toAdminDetail(admin, converter.toRoleOptions(loadRoles(adminId)));
    }

    /**
     * 创建管理员账号，并按需写入初始角色关系。
     *
     * @param request 创建入参
     * @return 创建后的管理员详情
     */
    @Override
    @Transactional
    public AdminDetailResponse create(AdminCreateRequest request) {
        String username = AdminCredentialPolicy.normalizeUsername(request.username());
        if (!AdminCredentialPolicy.isValidUsername(username)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "用户名长度需为 " + AdminCredentialPolicy.USERNAME_MIN_LENGTH + "—"
                            + AdminCredentialPolicy.USERNAME_MAX_LENGTH + " 个字符");
        }
        if (!AdminCredentialPolicy.isValidNewPassword(request.password())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "密码长度需为 " + AdminCredentialPolicy.PASSWORD_MIN_LENGTH + "—"
                            + AdminCredentialPolicy.PASSWORD_MAX_LENGTH + " 个字符");
        }
        List<Long> roleIds = SystemIds.toLongList(request.roleIds(), "角色 ID");
        List<SysRoleEntity> roles = requireRoles(roleIds);

        LocalDateTime now = SystemTimes.nowUtc();
        Long operator = currentAdmin.adminId();
        SysAdminEntity admin = new SysAdminEntity();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(request.password()));
        admin.setDisplayName(request.displayName().trim());
        admin.setStatus(AdminAccountStatus.enabled.name());
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        admin.setCreatedBy(operator);
        admin.setUpdatedBy(operator);
        adminMapper.insert(admin);

        for (SysRoleEntity role : roles) {
            adminRoleMapper.insertRelation(admin.getId(), role.getId(), now, operator);
        }
        log.info("已创建管理员 [{}]，角色数量 {}", username, roles.size());
        return converter.toAdminDetail(admin, converter.toRoleOptions(roles));
    }

    /**
     * 修改管理员显示名称。
     *
     * @param adminId 管理员 ID
     * @param request 修改入参
     * @return 修改后的管理员详情
     */
    @Override
    @Transactional
    public AdminDetailResponse update(Long adminId, AdminUpdateRequest request) {
        SysAdminEntity admin = requireAdmin(adminId);
        LocalDateTime now = SystemTimes.nowUtc();
        SysAdminEntity update = new SysAdminEntity();
        update.setId(adminId);
        update.setDisplayName(request.displayName().trim());
        update.setUpdatedAt(now);
        update.setUpdatedBy(currentAdmin.adminId());
        adminMapper.updateById(update);

        admin.setDisplayName(update.getDisplayName());
        admin.setUpdatedAt(now);
        return converter.toAdminDetail(admin, converter.toRoleOptions(loadRoles(adminId)));
    }

    /**
     * 启用或停用管理员，并在停用后撤销该账号全部令牌。
     *
     * <p>条件更新只匹配“目标状态与当前状态不同”的行：受影响行数为 0 说明账号不存在或状态已经一致，
     * 两种情况都按状态冲突返回 409，而不是当成修改成功。当前登录管理员不允许停用自己。</p>
     *
     * @param adminId 管理员 ID
     * @param request 状态入参
     * @return 修改后的管理员详情
     */
    @Override
    @Transactional
    public AdminDetailResponse changeStatus(Long adminId, AdminStatusRequest request) {
        SysAdminEntity admin = requireAdmin(adminId);
        String targetStatus = request.status();
        Long operator = currentAdmin.adminId();
        if (!AdminAccountStatus.isEnabled(targetStatus) && adminId.equals(operator)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能停用当前登录的管理员账号");
        }
        LocalDateTime now = SystemTimes.nowUtc();
        SysAdminEntity update = new SysAdminEntity();
        update.setStatus(targetStatus);
        update.setUpdatedAt(now);
        update.setUpdatedBy(operator);
        int affected = adminMapper.update(update, Wrappers.<SysAdminEntity>lambdaUpdate()
                .eq(SysAdminEntity::getId, adminId)
                .ne(SysAdminEntity::getStatus, targetStatus));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号状态已是最新值或账号不存在，请刷新后重试");
        }
        if (!AdminAccountStatus.isEnabled(targetStatus)) {
            tokenService.revokeAllForAdmin(adminId);
            log.info("管理员 [{}] 已停用并撤销全部令牌", adminId);
        }
        admin.setStatus(targetStatus);
        admin.setUpdatedAt(now);
        return converter.toAdminDetail(admin, converter.toRoleOptions(loadRoles(adminId)));
    }

    /**
     * 重置管理员密码并撤销该账号全部令牌。
     *
     * @param adminId 管理员 ID
     * @param request 密码重置入参
     */
    @Override
    @Transactional
    public void resetPassword(Long adminId, AdminPasswordResetRequest request) {
        requireAdmin(adminId);
        String password = request.newPassword();
        if (!AdminCredentialPolicy.isValidNewPassword(password)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "密码长度需为 " + AdminCredentialPolicy.PASSWORD_MIN_LENGTH + "—"
                            + AdminCredentialPolicy.PASSWORD_MAX_LENGTH + " 个字符");
        }
        LocalDateTime now = SystemTimes.nowUtc();
        SysAdminEntity update = new SysAdminEntity();
        update.setId(adminId);
        update.setPasswordHash(passwordEncoder.encode(password));
        update.setUpdatedAt(now);
        update.setUpdatedBy(currentAdmin.adminId());
        adminMapper.updateById(update);
        tokenService.revokeAllForAdmin(adminId);
        log.info("管理员 [{}] 的密码已重置，全部令牌已撤销", adminId);
    }

    /**
     * 全量替换管理员的角色关系。
     *
     * <p>关系表物理删除后重建：提交的集合即最终结果，重复提交同一角色不会产生重复插入；
     * 数据库提交成功后递增全局权限版本，使权限缓存立即失效。</p>
     *
     * @param adminId 管理员 ID
     * @param request 角色分配入参
     * @return 修改后的管理员详情
     */
    @Override
    @Transactional
    public AdminDetailResponse replaceRoles(Long adminId, AdminRoleAssignRequest request) {
        SysAdminEntity admin = requireAdmin(adminId);
        List<Long> roleIds = SystemIds.toLongList(request.roleIds(), "角色 ID");
        List<SysRoleEntity> roles = requireRoles(roleIds);

        LocalDateTime now = SystemTimes.nowUtc();
        Long operator = currentAdmin.adminId();
        adminRoleMapper.deleteByAdminId(adminId);
        for (SysRoleEntity role : roles) {
            adminRoleMapper.insertRelation(adminId, role.getId(), now, operator);
        }
        permissionCacheEvictor.evictAfterCommit();
        log.info("管理员 [{}] 的角色已全量替换为 {} 个", adminId, roles.size());
        return converter.toAdminDetail(admin, converter.toRoleOptions(roles));
    }

    /**
     * 构造管理员分页查询条件。
     *
     * <p>用户名按规范化规则处理后再模糊匹配，与保存、登录使用同一套大小写口径；
     * 状态为精确匹配。排序按主键升序，保证翻页结果稳定。</p>
     *
     * @param query 筛选条件，允许为 {@code null}
     * @return 类型安全的查询条件
     */
    private LambdaQueryWrapper<SysAdminEntity> buildAdminQuery(AdminQuery query) {
        LambdaQueryWrapper<SysAdminEntity> wrapper = Wrappers.<SysAdminEntity>lambdaQuery();
        if (query != null) {
            String username = AdminCredentialPolicy.normalizeUsername(query.username());
            wrapper.like(TextValues.hasText(username), SysAdminEntity::getUsername, username);
            wrapper.eq(TextValues.hasText(query.status()), SysAdminEntity::getStatus, query.status());
        }
        return wrapper.orderByAsc(SysAdminEntity::getId);
    }

    /**
     * 查询管理员实体，不存在时按资源不存在失败。
     *
     * @param adminId 管理员 ID
     * @return 管理员实体
     * @throws BusinessException 账号不存在时抛出 404
     */
    private SysAdminEntity requireAdmin(Long adminId) {
        SysAdminEntity admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "管理员账号不存在");
        }
        return admin;
    }

    /**
     * 校验角色 ID 全部存在并返回对应实体。
     *
     * @param roleIds 角色 ID 列表，允许为空
     * @return 角色实体列表，入参为空时返回空列表
     * @throws BusinessException 任一角色不存在时抛出 400
     */
    private List<SysRoleEntity> requireRoles(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<SysRoleEntity> roles = roleMapper.selectByIds(roleIds);
        Set<Long> found = roles.stream().map(SysRoleEntity::getId).collect(Collectors.toSet());
        if (found.size() != roleIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提交的角色中包含不存在或已删除的角色");
        }
        Map<Long, SysRoleEntity> byId = roles.stream()
                .collect(Collectors.toMap(SysRoleEntity::getId, Function.identity()));
        List<SysRoleEntity> ordered = new ArrayList<>(roleIds.size());
        for (Long roleId : roleIds) {
            ordered.add(byId.get(roleId));
        }
        return List.copyOf(ordered);
    }

    /**
     * 查询管理员当前拥有的角色。
     *
     * @param adminId 管理员 ID
     * @return 角色实体列表，没有角色时返回空列表
     */
    private List<SysRoleEntity> loadRoles(Long adminId) {
        List<Long> roleIds = adminRoleMapper.selectRoleIdsByAdminId(adminId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectByIds(roleIds);
    }

    /**
     * 批量查询多个管理员的角色，避免列表接口逐行查询。
     *
     * @param adminIds 管理员 ID 集合，允许为空
     * @return 管理员 ID 到角色列表的映射，无关联的管理员不出现在映射中
     */
    private Map<Long, List<SysRoleEntity>> loadRolesByAdmin(Collection<Long> adminIds) {
        if (adminIds == null || adminIds.isEmpty()) {
            return Map.of();
        }
        List<SysAdminRoleMapper.AdminRoleRelation> relations = adminRoleMapper.selectRelationsByAdminIds(adminIds);
        if (relations.isEmpty()) {
            return Map.of();
        }
        Set<Long> roleIds = relations.stream()
                .map(SysAdminRoleMapper.AdminRoleRelation::roleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, SysRoleEntity> rolesById = roleMapper.selectByIds(roleIds).stream()
                .collect(Collectors.toMap(SysRoleEntity::getId, Function.identity()));
        Map<Long, List<SysRoleEntity>> rolesByAdmin = new HashMap<>();
        for (SysAdminRoleMapper.AdminRoleRelation relation : relations) {
            SysRoleEntity role = rolesById.get(relation.roleId());
            if (role != null) {
                rolesByAdmin.computeIfAbsent(relation.adminId(), key -> new ArrayList<>()).add(role);
            }
        }
        return Map.copyOf(rolesByAdmin);
    }
}
