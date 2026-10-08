package cn.orangenode.forge.member.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.security.AuthenticatedAdmin;
import cn.orangenode.forge.member.converter.MemberConverter;
import cn.orangenode.forge.member.entity.MemberDeviceEntity;
import cn.orangenode.forge.member.entity.MemberUserEntity;
import cn.orangenode.forge.member.entity.MemberVipLogEntity;
import cn.orangenode.forge.member.mapper.MemberDeviceMapper;
import cn.orangenode.forge.member.mapper.MemberUserMapper;
import cn.orangenode.forge.member.mapper.MemberVipLogMapper;
import cn.orangenode.forge.member.request.MemberAdminPasswordRequest;
import cn.orangenode.forge.member.request.MemberStatusRequest;
import cn.orangenode.forge.member.request.MemberVipExtendRequest;
import cn.orangenode.forge.member.request.MemberVipSetRequest;
import cn.orangenode.forge.member.response.MemberDeviceResponse;
import cn.orangenode.forge.member.response.MemberUserResponse;
import cn.orangenode.forge.member.response.MemberVipLogResponse;
import cn.orangenode.forge.member.service.MemberManagementService;
import cn.orangenode.forge.member.service.MemberSessionService;
import cn.orangenode.forge.member.support.MemberOfflineReason;
import cn.orangenode.forge.member.support.MemberPaging;
import cn.orangenode.forge.member.support.MemberStatus;
import cn.orangenode.forge.member.support.MemberValues;

import lombok.RequiredArgsConstructor;

/**
 * 后台普通用户管理实现；VIP 调整锁定账号行并追加变更记录。
 */
@Service
@RequiredArgsConstructor
public class MemberManagementServiceImpl implements MemberManagementService {
    private static final String VIP_ACTIVE = "ACTIVE";
    private static final String VIP_EXPIRED = "EXPIRED";
    private static final String VIP_NONE = "NONE";
    private final MemberUserMapper users;
    private final MemberDeviceMapper devices;
    private final MemberVipLogMapper vipLogs;
    private final MemberSessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final MemberConverter converter;
    private final MemberPaging paging;

    /**
     * 按邮箱关键字、状态与 VIP 状态分页查询，按注册时间倒序。
     */
    @Override
    public PageResponse<MemberUserResponse> page(Integer pageNum, Integer pageSize, String username, String status,
            String vip) {
        LocalDateTime now = MemberValues.now();
        String keyword = MemberValues.clean(username);
        String state = MemberValues.clean(status);
        String vipState = MemberValues.clean(vip);
        if (state != null && !Set.of(MemberStatus.ENABLED.name(), MemberStatus.DISABLED.name()).contains(state)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账号状态只能是 ENABLED 或 DISABLED");
        }
        if (vipState != null && !Set.of(VIP_ACTIVE, VIP_EXPIRED, VIP_NONE).contains(vipState)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "VIP 状态只能是 ACTIVE、EXPIRED 或 NONE");
        }
        LambdaQueryWrapper<MemberUserEntity> query = Wrappers.<MemberUserEntity>lambdaQuery()
                .like(keyword != null, MemberUserEntity::getUsername, keyword)
                .eq(state != null, MemberUserEntity::getStatus, state)
                .gt(VIP_ACTIVE.equals(vipState), MemberUserEntity::getVipExpireAt, now)
                .le(VIP_EXPIRED.equals(vipState), MemberUserEntity::getVipExpireAt, now)
                .isNull(VIP_NONE.equals(vipState), MemberUserEntity::getVipExpireAt)
                .orderByDesc(MemberUserEntity::getCreatedAt).orderByDesc(MemberUserEntity::getId);
        Page<MemberUserEntity> page = users.selectPage(paging.page(pageNum, pageSize), query);
        return new PageResponse<>(page.getRecords().stream().map(this::toResponse).toList(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }

    /**
     * 查询详情。
     */
    @Override
    public MemberUserResponse detail(Long id) {
        return toResponse(require(id));
    }

    /**
     * 条件更新状态，停用时撤销全部令牌。
     */
    @Override
    @Transactional
    public MemberUserResponse changeStatus(Long id, MemberStatusRequest request) {
        MemberUserEntity user = require(id);
        int changed = users.update(null, Wrappers.<MemberUserEntity>lambdaUpdate()
                .set(MemberUserEntity::getStatus, request.status())
                .set(MemberUserEntity::getUpdatedAt, MemberValues.now())
                .eq(MemberUserEntity::getId, id).ne(MemberUserEntity::getStatus, request.status()));
        if (changed == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号已是该状态");
        }
        if (MemberStatus.DISABLED.name().equals(request.status())) {
            sessions.revokeAll(user.getId(), MemberOfflineReason.REVOKED);
        }
        return detail(id);
    }

    /**
     * 重置密码并撤销全部令牌。
     */
    @Override
    @Transactional
    public void resetPassword(Long id, MemberAdminPasswordRequest request) {
        require(id);
        users.update(null, Wrappers.<MemberUserEntity>lambdaUpdate()
                .set(MemberUserEntity::getPasswordHash, passwordEncoder.encode(request.password()))
                .set(MemberUserEntity::getUpdatedAt, MemberValues.now())
                .eq(MemberUserEntity::getId, id));
        sessions.revokeAll(id, MemberOfflineReason.REVOKED);
    }

    /**
     * 未开通或已过期从当前时刻起算，否则在原到期时间上顺延。
     */
    @Override
    @Transactional
    public MemberUserResponse extendVip(Long id, MemberVipExtendRequest request) {
        MemberUserEntity user = lock(id);
        LocalDateTime now = MemberValues.now();
        LocalDateTime before = user.getVipExpireAt();
        LocalDateTime base = before == null || !before.isAfter(now) ? now : before;
        LocalDateTime after = base.plusDays(request.days());
        writeVip(user, "EXTEND", request.days(), before, after, request.remark());
        return detail(id);
    }

    /**
     * 直接设置或取消到期时间。
     */
    @Override
    @Transactional
    public MemberUserResponse setVip(Long id, MemberVipSetRequest request) {
        MemberUserEntity user = lock(id);
        LocalDateTime after = MemberValues.utc(request.expireAt());
        writeVip(user, after == null ? "CANCEL" : "SET", null, user.getVipExpireAt(), after, request.remark());
        return detail(id);
    }

    /**
     * 查询 VIP 变更记录。
     */
    @Override
    public List<MemberVipLogResponse> vipLogs(Long id) {
        require(id);
        return vipLogs.selectList(Wrappers.<MemberVipLogEntity>lambdaQuery().eq(MemberVipLogEntity::getMemberId, id)
                .orderByDesc(MemberVipLogEntity::getId)).stream().map(converter::vipLog).toList();
    }

    /**
     * 分页查询登录设备，在线优先、最近登录在前。
     */
    @Override
    public PageResponse<MemberDeviceResponse> devices(Integer pageNum, Integer pageSize, Long memberId, String username,
            Boolean online) {
        Page<MemberDeviceEntity> request = paging.page(pageNum, pageSize);
        LambdaQueryWrapper<MemberDeviceEntity> query = Wrappers.<MemberDeviceEntity>lambdaQuery()
                .eq(memberId != null, MemberDeviceEntity::getMemberId, memberId)
                .eq(online != null, MemberDeviceEntity::getOnline, online);
        String keyword = MemberValues.clean(username);
        if (keyword != null) {
            List<Long> ids = users.selectList(Wrappers.<MemberUserEntity>lambdaQuery()
                    .like(MemberUserEntity::getUsername, keyword)).stream().map(MemberUserEntity::getId).toList();
            if (ids.isEmpty()) {
                return new PageResponse<>(List.of(), 0, (int) request.getCurrent(), (int) request.getSize());
            }
            query.in(MemberDeviceEntity::getMemberId, ids);
        }
        query.orderByDesc(MemberDeviceEntity::getOnline).orderByDesc(MemberDeviceEntity::getLoginAt)
                .orderByDesc(MemberDeviceEntity::getId);
        Page<MemberDeviceEntity> page = devices.selectPage(request, query);
        List<Long> memberIds = page.getRecords().stream().map(MemberDeviceEntity::getMemberId).distinct().toList();
        Map<Long, String> usernames = memberIds.isEmpty() ? Map.of() : users.selectByIds(memberIds).stream()
                .collect(Collectors.toMap(MemberUserEntity::getId, MemberUserEntity::getUsername, (left, right) -> left));
        return new PageResponse<>(page.getRecords().stream()
                .map(device -> converter.device(device, usernames.get(device.getMemberId()))).toList(),
                page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /**
     * 强制单个设备下线。
     */
    @Override
    public void forceOffline(Long deviceId) {
        sessions.forceOffline(deviceId);
    }

    /**
     * 强制全部设备下线：递增会话版本，使此前全部令牌失效。
     */
    @Override
    @Transactional
    public void forceOfflineAll(Long memberId) {
        require(memberId);
        sessions.revokeAll(memberId, MemberOfflineReason.ADMIN);
    }

    /**
     * 写入到期时间并追加变更记录。
     */
    private void writeVip(MemberUserEntity user, String type, Integer days, LocalDateTime before, LocalDateTime after,
            String remark) {
        LocalDateTime now = MemberValues.now();
        users.update(null, Wrappers.<MemberUserEntity>lambdaUpdate()
                .set(MemberUserEntity::getVipExpireAt, after)
                .set(MemberUserEntity::getUpdatedAt, now)
                .eq(MemberUserEntity::getId, user.getId()));
        MemberVipLogEntity log = new MemberVipLogEntity();
        log.setMemberId(user.getId());
        log.setChangeType(type);
        log.setDays(days);
        log.setBeforeExpireAt(before);
        log.setAfterExpireAt(after);
        log.setRemark(MemberValues.clean(remark));
        log.setOperatorId(currentAdminId());
        log.setCreatedAt(now);
        vipLogs.insert(log);
    }

    /**
     * 转换为后台响应并统计在线设备数。
     */
    private MemberUserResponse toResponse(MemberUserEntity user) {
        long online = devices.selectCount(Wrappers.<MemberDeviceEntity>lambdaQuery()
                .eq(MemberDeviceEntity::getMemberId, user.getId()).eq(MemberDeviceEntity::getOnline, true));
        return converter.user(user, online);
    }

    /**
     * 读取用户，不存在返回 404。
     */
    private MemberUserEntity require(Long id) {
        MemberUserEntity user = users.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /**
     * 锁定用户行，串行化 VIP 调整。
     */
    private MemberUserEntity lock(Long id) {
        MemberUserEntity user = users.selectOne(Wrappers.<MemberUserEntity>lambdaQuery()
                .eq(MemberUserEntity::getId, id).last("FOR UPDATE"));
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /**
     * 从安全上下文读取当前管理员 ID。
     */
    private Long currentAdminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedAdmin admin
                ? admin.id() : null;
    }
}
