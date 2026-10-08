package cn.orangenode.forge.video.service.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.orangenode.forge.framework.catalog.VideoCard;
import cn.orangenode.forge.framework.catalog.VideoCatalogPort;
import cn.orangenode.forge.video.entity.CategoryEntity;
import cn.orangenode.forge.video.entity.VideoEntity;
import lombok.RequiredArgsConstructor;

/**
 * 影片目录只读端口实现，只输出已上架且分类可见的影片卡片。
 */
@Component
@RequiredArgsConstructor
public class VideoCatalogPortImpl implements VideoCatalogPort {
    private final VideoStore store;

    /**
     * 读取分类子树内最近更新的公开影片。
     */
    @Override
    public List<VideoCard> latestByCategory(Long categoryId, int limit) {
        if (categoryId == null || limit < 1 || !store.categoryVisible(categoryId)) {
            return List.of();
        }
        List<Long> categoryIds = visibleSubtree(categoryId);
        Page<VideoEntity> page = store.videos.selectPage(new Page<>(1, limit, false),
                Wrappers.<VideoEntity>lambdaQuery().in(VideoEntity::getCategoryId, categoryIds)
                        .eq(VideoEntity::getPublished, true)
                        .orderByDesc(VideoEntity::getUpdatedAt).orderByDesc(VideoEntity::getId));
        return page.getRecords().stream().map(this::card).toList();
    }

    /**
     * 按入参顺序读取公开影片，跳过下架、删除或分类不可见的影片。
     */
    @Override
    public List<VideoCard> findPublic(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, VideoEntity> found = store.videos.selectList(Wrappers.<VideoEntity>lambdaQuery()
                .in(VideoEntity::getId, ids).eq(VideoEntity::getPublished, true)).stream()
                .collect(Collectors.toMap(VideoEntity::getId, Function.identity()));
        List<VideoCard> cards = new ArrayList<>();
        for (Long id : ids) {
            VideoEntity video = found.get(id);
            if (video != null && store.categoryVisible(video.getCategoryId())) {
                cards.add(card(video));
            }
        }
        return List.copyOf(cards);
    }

    /**
     * 读取未删除影片名称。
     */
    @Override
    public Optional<String> videoName(Long videoId) {
        return Optional.ofNullable(videoId == null ? null : store.videos.selectById(videoId)).map(VideoEntity::getName);
    }

    /**
     * 读取未删除分类名称。
     */
    @Override
    public Optional<String> categoryName(Long categoryId) {
        return Optional.ofNullable(categoryId == null ? null : store.categories.selectById(categoryId))
                .map(CategoryEntity::getName);
    }

    /**
     * 收集分类及其启用后代，停用节点的后代一并排除。
     */
    private List<Long> visibleSubtree(Long rootId) {
        Map<Long, List<CategoryEntity>> children = store.categories.selectList(Wrappers.<CategoryEntity>lambdaQuery()).stream()
                .filter(category -> Boolean.TRUE.equals(category.getEnabled()))
                .collect(Collectors.groupingBy(category -> category.getParentId() == null ? 0L : category.getParentId()));
        Set<Long> result = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>(List.of(rootId));
        while (!pending.isEmpty()) {
            Long current = pending.pop();
            if (result.add(current)) {
                children.getOrDefault(current, List.of()).forEach(child -> pending.push(child.getId()));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 转换为对外卡片，不含播放地址。
     */
    private VideoCard card(VideoEntity video) {
        return new VideoCard(video.getId(), video.getName(), video.getPosterUrl(), video.getRemarks(),
                video.getYear(), video.getCategoryId());
    }
}
