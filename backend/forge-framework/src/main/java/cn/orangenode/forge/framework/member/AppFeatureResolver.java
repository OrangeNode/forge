package cn.orangenode.forge.framework.member;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import cn.orangenode.forge.framework.config.ForgeMemberAuthProperties;

/**
 * App 功能开关读取入口：优先使用业务模块实现，未装配时退回配置默认值。
 */
@Component
public class AppFeatureResolver {

    /**
     * 功能开关端口提供者。
     */
    private final ObjectProvider<AppFeatureSwitches> switches;

    /**
     * 普通用户认证配置，提供默认值。
     */
    private final ForgeMemberAuthProperties properties;

    /**
     * 构造功能开关读取入口。
     *
     * @param switches   功能开关端口提供者
     * @param properties 普通用户认证配置
     */
    public AppFeatureResolver(ObjectProvider<AppFeatureSwitches> switches, ForgeMemberAuthProperties properties) {
        this.switches = switches;
        this.properties = properties;
    }

    /**
     * 读取当前开关快照。
     *
     * @return 开关快照
     */
    public AppFeatureSnapshot current() {
        AppFeatureSwitches provider = switches.getIfAvailable();
        if (provider != null) {
            return provider.current();
        }
        return new AppFeatureSnapshot(properties.isDefaultVipRequired(), properties.isDefaultRegistrationOpen(),
                properties.getDefaultMaxDevices());
    }
}
