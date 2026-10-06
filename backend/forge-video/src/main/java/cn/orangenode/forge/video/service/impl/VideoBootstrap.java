package cn.orangenode.forge.video.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import cn.orangenode.forge.video.config.VideoProperties;
import cn.orangenode.forge.video.entity.SettingsEntity;
import cn.orangenode.forge.video.entity.SourceEntity;
import cn.orangenode.forge.video.support.VideoValues;
import lombok.RequiredArgsConstructor;

/**
 * 迁移后初始化缺失的配置，不覆盖管理员修改、不启动全量任务。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VideoBootstrap {
    private final VideoStore store;
    private final VideoProperties properties;

    /**
     * 首次安装读取默认值，已有数据保持原样。
     */
    @Order(0)
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initialize() {
        if (store.settings.selectById(1L) == null) {
            SettingsEntity rules = new SettingsEntity();
            rules.setId(1L);
            rules.setMatchFields(String.join(",", properties.getDefaultMatchFields()));
            rules.setUpdateFields(String.join(",", properties.getDefaultUpdateFields()));
            rules.setAutoPublish(properties.isAutoPublish());
            rules.setCreatedAt(VideoValues.now());
            rules.setUpdatedAt(VideoValues.now());
            store.settings.insert(rules);
        }
        for (VideoProperties.SeedSource seed : properties.getSources()) {
            if (store.sources.countSeed(seed.getName(), seed.getApiUrl()) == 0) {
                SourceEntity e = new SourceEntity();
                e.setName(seed.getName());
                e.setApiUrl(seed.getApiUrl());
                e.setSourceZone(properties.getDefaultZone());
                e.setEnabled(true);
                e.setDeleted(0);
                e.setCreatedAt(VideoValues.now());
                e.setUpdatedAt(VideoValues.now());
                store.sources.insert(e);
            }
        }
    }
}
