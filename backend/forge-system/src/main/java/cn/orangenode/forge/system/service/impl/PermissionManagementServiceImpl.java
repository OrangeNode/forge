package cn.orangenode.forge.system.service.impl;

import java.time.LocalDateTime;

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
import cn.orangenode.forge.system.converter.SystemConverter;
import cn.orangenode.forge.system.entity.SysPermissionEntity;
import cn.orangenode.forge.system.mapper.SysPermissionMapper;
import cn.orangenode.forge.system.request.PermissionCreateRequest;
import cn.orangenode.forge.system.request.PermissionQuery;
import cn.orangenode.forge.system.request.PermissionUpdateRequest;
import cn.orangenode.forge.system.response.PermissionResponse;
import cn.orangenode.forge.system.service.PermissionManagementService;
import cn.orangenode.forge.system.support.PermissionCacheEvictor;
import cn.orangenode.forge.system.support.PermissionCodeFormat;
import cn.orangenode.forge.system.support.SystemCurrentAdmin;
import cn.orangenode.forge.system.support.SystemTimes;
import cn.orangenode.forge.system.support.TextValues;

import lombok.extern.slf4j.Slf4j;

/**
 * 权限管理用例实现。
 *
 * <p>权限代码是 {@code @PreAuthorize} 的唯一依据：创建时按 {@code 模块:资源:动作} 校验格式，
 * 唯一性由数据库唯一约束兜底，冲突映射为 409 且不回显索引名；修改只允许改名称与说明，
 * 被角色引用时不允许删除。</p>
 *
 * <p>任何权限变更提交后都使全部权限缓存立即失效：超级管理员的权限集合由全部有效权限代码推导，
 * 新增或删除权限同样会改变解析结果，因此不能只在授权关系变更时才失效缓存。</p>
 */
@Slf4j
@Service
public class PermissionManagementServiceImpl implements PermissionManagementService {

    /**
     * 权限数据访问。
     */
    private final SysPermissionMapper permissionMapper;

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
     * 构造权限管理用例实现。
     *
     * @param permissionMapper       权限数据访问
     * @param permissionCacheEvictor 权限缓存失效入口
     * @param currentAdmin           当前操作者解析
     * @param converter              响应转换器
     */
    public PermissionManagementServiceImpl(SysPermissionMapper permissionMapper,
            PermissionCacheEvictor permissionCacheEvictor, SystemCurrentAdmin currentAdmin,
            SystemConverter converter) {
        this.permissionMapper = permissionMapper;
        this.permissionCacheEvictor = permissionCacheEvictor;
        this.currentAdmin = currentAdmin;
        this.converter = converter;
    }

    /**
     * 分页查询权限。
     *
     * @param pageRequest 分页入参
     * @param query       筛选条件，允许为 {@code null}
     * @return 权限分页结果
     */
    @Override
    public PageResponse<PermissionResponse> page(PageRequest pageRequest, PermissionQuery query) {
        Page<SysPermissionEntity> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        IPage<SysPermissionEntity> result = permissionMapper.selectPage(page, buildPermissionQuery(query));
        return PageResponses.from(result, converter::toPermission);
    }

    /**
     * 创建权限。
     *
     * @param request 创建入参
     * @return 创建后的权限信息
     */
    @Override
    @Transactional
    public PermissionResponse create(PermissionCreateRequest request) {
        String code = PermissionCodeFormat.normalize(request.code());
        if (!PermissionCodeFormat.isValid(code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, PermissionCodeFormat.DESCRIPTION);
        }
        LocalDateTime now = SystemTimes.nowUtc();
        Long operator = currentAdmin.adminId();
        SysPermissionEntity permission = new SysPermissionEntity();
        permission.setCode(code);
        permission.setName(request.name().trim());
        permission.setDescription(TextValues.trimToNull(request.description()));
        permission.setDeleted(0);
        permission.setCreatedAt(now);
        permission.setUpdatedAt(now);
        permission.setCreatedBy(operator);
        permission.setUpdatedBy(operator);
        permissionMapper.insert(permission);
        permissionCacheEvictor.evictAfterCommit();
        log.info("已创建权限 [{}]", code);
        return converter.toPermission(permission);
    }

    /**
     * 修改权限名称与说明。
     *
     * @param permissionId 权限 ID
     * @param request      修改入参
     * @return 修改后的权限信息
     */
    @Override
    @Transactional
    public PermissionResponse update(Long permissionId, PermissionUpdateRequest request) {
        SysPermissionEntity permission = requirePermission(permissionId);
        LocalDateTime now = SystemTimes.nowUtc();
        SysPermissionEntity update = new SysPermissionEntity();
        update.setId(permissionId);
        update.setName(request.name().trim());
        update.setDescription(TextValues.trimToNull(request.description()));
        update.setUpdatedAt(now);
        update.setUpdatedBy(currentAdmin.adminId());
        permissionMapper.updateById(update);

        permission.setName(update.getName());
        permission.setDescription(update.getDescription());
        permission.setUpdatedAt(now);
        permissionCacheEvictor.evictAfterCommit();
        return converter.toPermission(permission);
    }

    /**
     * 逻辑删除权限。
     *
     * @param permissionId 权限 ID
     */
    @Override
    @Transactional
    public void delete(Long permissionId) {
        SysPermissionEntity permission = requirePermission(permissionId);
        if (permissionMapper.countRoleReferences(permissionId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该权限已授予角色，请先解除授权再删除");
        }
        permissionMapper.deleteById(permissionId);
        permissionCacheEvictor.evictAfterCommit();
        log.info("已删除权限 [{}]", permission.getCode());
    }

    /**
     * 构造权限分页查询条件。
     *
     * @param query 筛选条件，允许为 {@code null}
     * @return 类型安全的查询条件
     */
    private LambdaQueryWrapper<SysPermissionEntity> buildPermissionQuery(PermissionQuery query) {
        LambdaQueryWrapper<SysPermissionEntity> wrapper = Wrappers.<SysPermissionEntity>lambdaQuery()
                .orderByAsc(SysPermissionEntity::getCode);
        if (query != null && TextValues.hasText(query.code())) {
            wrapper.like(SysPermissionEntity::getCode, query.code().trim());
        }
        if (query != null && TextValues.hasText(query.name())) {
            wrapper.like(SysPermissionEntity::getName, query.name().trim());
        }
        return wrapper;
    }

    /**
     * 查询权限实体，不存在时按资源不存在失败。
     *
     * @param permissionId 权限 ID
     * @return 权限实体
     * @throws BusinessException 权限不存在或已删除时抛出 404
     */
    private SysPermissionEntity requirePermission(Long permissionId) {
        SysPermissionEntity permission = permissionMapper.selectById(permissionId);
        if (permission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "权限不存在或已删除");
        }
        return permission;
    }
}
