package cn.orangenode.forge.video.service.impl;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.orangenode.forge.video.entity.VideoEntity;
import cn.orangenode.forge.video.support.PinyinInitials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 启动后为历史影片回填片名拼音首字母；按主键分批处理，失败只记日志，不影响启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoInitialsBackfill {
    private static final int BATCH = 500;
    private final VideoStore store;

    /**
     * 回填缺少首字母的影片，已回填的记录不会再次处理。
     */
    @Order(10)
    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        long lastId = 0;
        int total = 0;
        try {
            while (true) {
                List<VideoEntity> batch = store.videos.selectPage(new Page<>(1, BATCH, false),
                        Wrappers.<VideoEntity>lambdaQuery().select(VideoEntity::getId, VideoEntity::getName)
                                .isNull(VideoEntity::getNameInitials).gt(VideoEntity::getId, lastId)
                                .orderByAsc(VideoEntity::getId)).getRecords();
                if (batch.isEmpty()) {
                    break;
                }
                for (VideoEntity video : batch) {
                    store.videos.update(null, Wrappers.<VideoEntity>lambdaUpdate()
                            .set(VideoEntity::getNameInitials, PinyinInitials.of(video.getName()))
                            .eq(VideoEntity::getId, video.getId()).isNull(VideoEntity::getNameInitials));
                    lastId = video.getId();
                }
                total += batch.size();
            }
            if (total > 0) {
                log.info("已为 {} 部历史影片回填片名拼音首字母", total);
            }
        } catch (RuntimeException failure) {
            log.warn("回填片名拼音首字母失败，已处理 {} 部，下次启动继续", total, failure);
        }
    }
}
