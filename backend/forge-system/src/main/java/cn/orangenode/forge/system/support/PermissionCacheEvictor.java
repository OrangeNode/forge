package cn.orangenode.forge.system.support;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import cn.orangenode.forge.system.service.AdminAuthorityService;

import lombok.extern.slf4j.Slf4j;

/**
 * 权限缓存失效入口。
 *
 * <p>角色、权限、菜单可见性与管理员角色关系的数据变更提交成功后，递增全局权限版本，
 * 使全部权限缓存立即失效。选择“提交后失效”而不是“写入前失效”：回滚的变更不能让缓存提前失效，
 * 也不缓存未提交状态；没有活动事务时立即失效，避免调用方漏掉事务边界时缓存永远不刷新。</p>
 *
 * <p>失效本身失败只记录错误日志，不把已提交的业务操作改写成失败：数据已经落库，
 * 此时对调用方报错会与真实结果不符，调用方重试还会造成重复变更。此时旧缓存仍带存活时间，
 * 最迟会在缓存存活时间到期后重新解析数据库；日志给出了需要人工核对的键。</p>
 */
@Slf4j
@Component
public class PermissionCacheEvictor {

    /**
     * 权限解析服务，负责递增全局权限版本。
     */
    private final AdminAuthorityService authorityService;

    /**
     * 构造权限缓存失效入口。
     *
     * @param authorityService 权限解析服务
     */
    public PermissionCacheEvictor(AdminAuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    /**
     * 在当前事务提交后使全部权限缓存失效。
     *
     * <p>由所有会改变授权结果的写用例在数据修改完成后调用。</p>
     */
    public void evictAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictSafely();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 事务提交后递增权限版本。
             *
             * @param status 提交状态，本实现不区分只读事务
             */
            @Override
            public void afterCommit() {
                evictSafely();
            }
        });
    }

    /**
     * 执行失效并隔离依赖故障。
     *
     * <p>Redis 不可用时记录错误日志，不向上抛出：业务数据已经提交，抛出只会让调用方看到
     * 与数据库不一致的失败结果。</p>
     */
    private void evictSafely() {
        try {
            authorityService.evictAllPermissions();
        } catch (RuntimeException failure) {
            log.error("权限缓存失效失败，旧缓存将按存活时间自然过期，请核对 Redis 状态", failure);
        }
    }
}
