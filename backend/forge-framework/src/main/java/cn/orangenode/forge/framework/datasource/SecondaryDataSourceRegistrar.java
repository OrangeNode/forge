package cn.orangenode.forge.framework.datasource;

import javax.sql.DataSource;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import cn.orangenode.forge.framework.config.ForgeSecondaryDataSourceProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 第二数据源注册器。
 *
 * <p>第二数据源是可选项：只有配置 {@code forge.secondary-datasource.enabled=true}
 * 且连接信息完整时才注册到 dynamic-datasource，未启用时完全不存在该名称。</p>
 *
 * <p>使用连接池的惰性初始化：注册阶段不建立连接，首个使用 {@code @DS} 的请求才真正连接，
 * 因此未启用或临时不可用的第二库不会影响应用启动与主库能力。</p>
 */
@Slf4j
@Component
public class SecondaryDataSourceRegistrar implements BeanPostProcessor {

    /**
     * 第二数据源配置。
     */
    private final ForgeSecondaryDataSourceProperties properties;

    /**
     * 构造第二数据源注册器。
     *
     * @param properties 第二数据源配置
     */
    public SecondaryDataSourceRegistrar(ForgeSecondaryDataSourceProperties properties) {
        this.properties = properties;
    }

    /**
     * 在路由数据源初始化前注册第二数据源。
     *
     * @param bean     容器创建的 Bean
     * @param beanName Bean 名称
     * @return 原 Bean；本处理器只做注册，不替换实例
     * @throws BeansException 注册失败时透传
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DynamicRoutingDataSource routingDataSource) {
            registerSecondary(routingDataSource);
        }
        return bean;
    }

    /**
     * 按配置注册第二数据源。
     *
     * @param routingDataSource 动态路由数据源
     */
    private void registerSecondary(DynamicRoutingDataSource routingDataSource) {
        String name = properties.getName();
        if (routingDataSource.getDataSources().containsKey(name)) {
            log.info("数据源 [{}] 已存在，跳过第二数据源注册", name);
            return;
        }
        if (!properties.isConfigured()) {
            log.info("第二数据源未启用或连接信息不完整，当前只注册主库；使用 @DS(\"{}\") 将明确报错", name);
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName(properties.getPoolName());
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName(properties.getDriverClassName());
        config.setMaximumPoolSize(properties.getMaximumPoolSize());
        config.setMinimumIdle(properties.getMinimumIdle());
        config.setInitializationFailTimeout(-1);
        DataSource dataSource = new HikariDataSource(config);
        routingDataSource.addDataSource(name, dataSource);
        log.info("第二数据源 [{}] 已注册，连接地址来自配置，凭据不写入日志", name);
    }
}
