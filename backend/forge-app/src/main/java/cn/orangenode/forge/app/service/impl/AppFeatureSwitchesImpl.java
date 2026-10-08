package cn.orangenode.forge.app.service.impl;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.app.entity.AppSettingsEntity;
import cn.orangenode.forge.framework.config.ForgeMemberAuthProperties;
import cn.orangenode.forge.framework.member.AppFeatureSnapshot;
import cn.orangenode.forge.framework.member.AppFeatureSwitches;

import lombok.RequiredArgsConstructor;

/**
 * 功能开关端口实现：每次读取数据库当前值，后台修改后下一次请求即生效；设置行缺失时退回配置默认值。
 */
@Component
@RequiredArgsConstructor
public class AppFeatureSwitchesImpl implements AppFeatureSwitches {
    private final AppSettingsStore store;
    private final ForgeMemberAuthProperties defaults;

    /**
     * 读取当前开关快照。
     */
    @Override
    public AppFeatureSnapshot current() {
        AppSettingsEntity settings = store.find();
        if (settings == null) {
            return new AppFeatureSnapshot(defaults.isDefaultVipRequired(), defaults.isDefaultRegistrationOpen(),
                    defaults.getDefaultMaxDevices());
        }
        return new AppFeatureSnapshot(Boolean.TRUE.equals(settings.getVipRequired()),
                Boolean.TRUE.equals(settings.getRegistrationOpen()), settings.getMaxDevices());
    }
}
