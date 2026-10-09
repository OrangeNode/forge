package cn.orangenode.forge.member.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.redis.ForgeRedisTemplate;
import cn.orangenode.forge.framework.security.AuthenticatedAdmin;
import cn.orangenode.forge.member.config.MemberProperties;
import cn.orangenode.forge.member.converter.MemberConverter;
import cn.orangenode.forge.member.entity.MemberActivationCodeEntity;
import cn.orangenode.forge.member.mapper.MemberActivationCodeMapper;
import cn.orangenode.forge.member.request.MemberActivationBatchRequest;
import cn.orangenode.forge.member.request.MemberActivationCreateRequest;
import cn.orangenode.forge.member.response.MemberActivationBatchResponse;
import cn.orangenode.forge.member.response.MemberActivationCodeResponse;
import cn.orangenode.forge.member.service.MemberActivationService;
import cn.orangenode.forge.member.support.MemberActivationStatus;
import cn.orangenode.forge.member.support.MemberPaging;
import cn.orangenode.forge.member.support.MemberValues;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 注册激活码实现。
 *
 * <p>激活码由安全随机数从去掉 0/O/1/I/L 的 31 个字符中生成；用户输入时忽略大小写、空格与短横线。
 * 核销用“状态仍为未使用且未过期”的条件更新完成，并发注册同一码时只有一个成功，另一个随注册事务回滚。
 * 输错激活码按来源地址每日计数，超过上限当天拒绝，防止穷举。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberActivationServiceImpl implements MemberActivationService {
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final String FAIL_KEY = "member:activation-fail:";
    private static final Duration DAY = Duration.ofDays(1);
    private static final int GENERATE_ATTEMPTS = 5;
    private final SecureRandom random = new SecureRandom();
    private final MemberActivationCodeMapper codes;
    private final MemberConverter converter;
    private final MemberPaging paging;
    private final ForgeRedisTemplate redis;
    private final MemberProperties properties;

    /**
     * 按关键字（激活码或使用者用户名）、状态与备注分页查询，按生成时间倒序。
     */
    @Override
    public PageResponse<MemberActivationCodeResponse> page(Integer pageNum, Integer pageSize, String keyword,
            String status, String remark) {
        Page<MemberActivationCodeEntity> page = codes.selectPage(paging.page(pageNum, pageSize),
                filterQuery(keyword, status, remark).orderByDesc(MemberActivationCodeEntity::getId));
        return new PageResponse<>(page.getRecords().stream().map(converter::activation).toList(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }

    /**
     * 与列表相同的筛选条件导出全部匹配项；超过上限返回 400，避免一次读出过多数据。
     */
    @Override
    public List<MemberActivationCodeResponse> export(String keyword, String status, String remark) {
        int max = properties.getActivation().getExportMax();
        if (codes.selectCount(filterQuery(keyword, status, remark)) > max) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "一次最多导出 " + max + " 个激活码，请缩小筛选范围");
        }
        return codes.selectList(filterQuery(keyword, status, remark).orderByDesc(MemberActivationCodeEntity::getId))
                .stream().map(converter::activation).toList();
    }

    /**
     * 条件更新只作废仍未使用的码，已使用、已作废或已不存在的计为跳过。
     */
    @Override
    @Transactional
    public MemberActivationBatchResponse revokeBatch(MemberActivationBatchRequest request) {
        List<Long> ids = batchIds(request);
        LocalDateTime now = MemberValues.now();
        int changed = codes.update(null, Wrappers.<MemberActivationCodeEntity>lambdaUpdate()
                .set(MemberActivationCodeEntity::getStatus, MemberActivationStatus.REVOKED.name())
                .set(MemberActivationCodeEntity::getRevokedAt, now)
                .set(MemberActivationCodeEntity::getUpdatedAt, now)
                .in(MemberActivationCodeEntity::getId, ids)
                .eq(MemberActivationCodeEntity::getStatus, MemberActivationStatus.UNUSED.name()));
        log.info("批量作废激活码：作废 {} 个，跳过 {} 个，管理员 ID={}", changed, ids.size() - changed, currentAdminId());
        return new MemberActivationBatchResponse(changed, ids.size() - changed);
    }

    /**
     * 物理删除未被使用过的码；已使用的码关联着注册账号，保留不删并计为跳过。
     */
    @Override
    @Transactional
    public MemberActivationBatchResponse deleteBatch(MemberActivationBatchRequest request) {
        List<Long> ids = batchIds(request);
        int deleted = codes.delete(Wrappers.<MemberActivationCodeEntity>lambdaQuery()
                .in(MemberActivationCodeEntity::getId, ids)
                .ne(MemberActivationCodeEntity::getStatus, MemberActivationStatus.USED.name()));
        log.info("批量删除激活码：删除 {} 个，跳过 {} 个，管理员 ID={}", deleted, ids.size() - deleted, currentAdminId());
        return new MemberActivationBatchResponse(deleted, ids.size() - deleted);
    }

    /**
     * 解析并去重批量操作的 ID，数量超过上限返回 400。
     */
    private List<Long> batchIds(MemberActivationBatchRequest request) {
        List<Long> ids = request.ids().stream().map(MemberValues::id).distinct().toList();
        int max = properties.getActivation().getBatchMax();
        if (ids.size() > max) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "一次最多处理 " + max + " 个激活码");
        }
        return ids;
    }

    /**
     * 按关键字（激活码或使用者用户名）、状态与备注构造查询条件，不含排序（计数查询不能带排序）。
     */
    private LambdaQueryWrapper<MemberActivationCodeEntity> filterQuery(String keyword, String status, String remark) {
        MemberActivationStatus filter = status(MemberValues.clean(status));
        LocalDateTime now = MemberValues.now();
        LambdaQueryWrapper<MemberActivationCodeEntity> query = Wrappers.lambdaQuery();
        String word = MemberValues.clean(keyword);
        if (word != null) {
            String code = normalize(word);
            query.and(inner -> inner.like(!code.isEmpty(), MemberActivationCodeEntity::getCode, code)
                    .or().like(MemberActivationCodeEntity::getUsedUsername, word));
        }
        String note = MemberValues.clean(remark);
        query.like(note != null, MemberActivationCodeEntity::getRemark, note);
        if (filter == MemberActivationStatus.UNUSED) {
            query.eq(MemberActivationCodeEntity::getStatus, MemberActivationStatus.UNUSED.name())
                    .and(inner -> inner.isNull(MemberActivationCodeEntity::getExpireAt)
                            .or().gt(MemberActivationCodeEntity::getExpireAt, now));
        } else if (filter == MemberActivationStatus.EXPIRED) {
            query.eq(MemberActivationCodeEntity::getStatus, MemberActivationStatus.UNUSED.name())
                    .le(MemberActivationCodeEntity::getExpireAt, now);
        } else if (filter != null) {
            query.eq(MemberActivationCodeEntity::getStatus, filter.name());
        }
        return query;
    }

    /**
     * 批量生成：数量不超过配置上限，每个码带会员天数，从注册时刻起算；随机码偶遇重复时换一个重试。
     */
    @Override
    @Transactional
    public List<MemberActivationCodeResponse> create(MemberActivationCreateRequest request) {
        int max = properties.getActivation().getBatchMax();
        if (request.count() > max) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "一次最多生成 " + max + " 个激活码");
        }
        LocalDateTime now = MemberValues.now();
        String remark = MemberValues.clean(request.remark());
        Long operator = currentAdminId();
        List<MemberActivationCodeResponse> created = new ArrayList<>(request.count());
        for (int i = 0; i < request.count(); i++) {
            MemberActivationCodeEntity entity = new MemberActivationCodeEntity();
            entity.setStatus(MemberActivationStatus.UNUSED.name());
            entity.setVipDays(request.vipDays());
            entity.setRemark(remark);
            entity.setCreatedBy(operator);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            insertWithFreshCode(entity);
            created.add(converter.activation(entity));
        }
        log.info("生成注册激活码 {} 个，会员 {} 天，管理员 ID={}，备注={}", request.count(), request.vipDays(), operator, remark);
        return created;
    }

    /**
     * 只作废未使用的激活码（含已过期）；已使用或已作废返回 409。
     */
    @Override
    @Transactional
    public MemberActivationCodeResponse revoke(Long id) {
        MemberActivationCodeEntity entity = codes.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "激活码不存在");
        }
        LocalDateTime now = MemberValues.now();
        int changed = codes.update(null, Wrappers.<MemberActivationCodeEntity>lambdaUpdate()
                .set(MemberActivationCodeEntity::getStatus, MemberActivationStatus.REVOKED.name())
                .set(MemberActivationCodeEntity::getRevokedAt, now)
                .set(MemberActivationCodeEntity::getUpdatedAt, now)
                .eq(MemberActivationCodeEntity::getId, id)
                .eq(MemberActivationCodeEntity::getStatus, MemberActivationStatus.UNUSED.name()));
        if (changed == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, MemberActivationStatus.USED.name().equals(entity.getStatus())
                    ? "激活码已被使用，不能作废" : "激活码已作废");
        }
        return converter.activation(codes.selectById(id));
    }

    /**
     * 校验激活码存在、未使用、未作废、未过期；失败计入来源地址当天的失败次数，超过上限返回 429。
     */
    @Override
    public Long check(String code, String clientIp) {
        String failKey = clientIp == null ? null : FAIL_KEY + LocalDate.now(ZoneOffset.UTC) + ":" + clientIp;
        if (failKey != null) {
            String count = redis.get(failKey);
            if (count != null && Long.parseLong(count) >= properties.getActivation().getIpDailyFailLimit()) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "激活码输错次数过多，请明天再试");
            }
        }
        String normalized = normalize(code);
        MemberActivationCodeEntity entity = normalized.isEmpty() ? null : codes.selectOne(
                Wrappers.<MemberActivationCodeEntity>lambdaQuery().eq(MemberActivationCodeEntity::getCode, normalized));
        String failure = null;
        if (entity == null) {
            failure = "激活码不正确";
        } else if (MemberActivationStatus.USED.name().equals(entity.getStatus())) {
            failure = "激活码已被使用";
        } else if (MemberActivationStatus.REVOKED.name().equals(entity.getStatus())) {
            failure = "激活码已作废";
        } else if (entity.getExpireAt() != null && !entity.getExpireAt().isAfter(MemberValues.now())) {
            failure = "激活码已过期";
        }
        if (failure != null) {
            if (failKey != null) {
                redis.increment(failKey, DAY);
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST, failure);
        }
        return entity.getId();
    }

    /**
     * 条件更新核销并返回该码的会员天数；没有更新到行说明刚被他人使用、作废或到期，返回 409 让注册事务回滚。
     */
    @Override
    public Integer consume(Long codeId, Long memberId, String username, String clientIp) {
        LocalDateTime now = MemberValues.now();
        int changed = codes.update(null, Wrappers.<MemberActivationCodeEntity>lambdaUpdate()
                .set(MemberActivationCodeEntity::getStatus, MemberActivationStatus.USED.name())
                .set(MemberActivationCodeEntity::getUsedMemberId, memberId)
                .set(MemberActivationCodeEntity::getUsedUsername, username)
                .set(MemberActivationCodeEntity::getUsedAt, now)
                .set(MemberActivationCodeEntity::getUsedIp, clientIp)
                .set(MemberActivationCodeEntity::getUpdatedAt, now)
                .eq(MemberActivationCodeEntity::getId, codeId)
                .eq(MemberActivationCodeEntity::getStatus, MemberActivationStatus.UNUSED.name())
                .and(inner -> inner.isNull(MemberActivationCodeEntity::getExpireAt)
                        .or().gt(MemberActivationCodeEntity::getExpireAt, now)));
        if (changed == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "激活码已被使用");
        }
        return codes.selectById(codeId).getVipDays();
    }

    /**
     * 解析状态筛选值，非法值返回 400。
     */
    private static MemberActivationStatus status(String value) {
        if (value == null) {
            return null;
        }
        try {
            return MemberActivationStatus.valueOf(value);
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "激活码状态只能是 UNUSED、USED、REVOKED 或 EXPIRED");
        }
    }

    /**
     * 生成随机码并插入；唯一键冲突时换一个码重试，多次仍冲突返回 500。
     */
    private void insertWithFreshCode(MemberActivationCodeEntity entity) {
        for (int attempt = 0; attempt < GENERATE_ATTEMPTS; attempt++) {
            entity.setCode(randomCode());
            try {
                codes.insert(entity);
                return;
            } catch (DuplicateKeyException duplicate) {
                entity.setId(null);
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生成激活码失败，请重试");
    }

    /**
     * 按配置长度生成随机激活码。
     */
    private String randomCode() {
        int length = properties.getActivation().getCodeLength();
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * 规范化用户输入：去掉字母数字以外的字符并转大写。
     */
    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
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
