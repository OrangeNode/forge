package cn.orangenode.forge.application.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cn.orangenode.forge.system.service.AdminAuthorityService;

/**
 * 迁移可能新增菜单权限，应用装配层在数据库就绪后使历史权限缓存失效。
 */
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class MigratedPermissionConfig {
    /**
     * 通过系统模块公开契约刷新缓存，不让业务模块互相依赖。
     */
    @Bean
    public ApplicationRunner migratedPermissionRefresh(AdminAuthorityService authority) {
        return arguments -> authority.evictAllPermissions();
    }
}
