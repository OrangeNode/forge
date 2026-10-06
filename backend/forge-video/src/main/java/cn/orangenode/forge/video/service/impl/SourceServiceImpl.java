package cn.orangenode.forge.video.service.impl;

import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.video.collector.MacCmsClient;
import cn.orangenode.forge.video.collector.MacCmsParser;
import cn.orangenode.forge.video.converter.VideoConverter;
import cn.orangenode.forge.video.entity.SourceEntity;
import cn.orangenode.forge.video.entity.SourceCategoryEntity;
import cn.orangenode.forge.video.entity.PendingEntity;
import cn.orangenode.forge.video.entity.OriginEntity;
import cn.orangenode.forge.video.entity.TaskEntity;
import cn.orangenode.forge.video.entity.RunEntity;
import cn.orangenode.forge.video.entity.CategoryEntity;
import cn.orangenode.forge.video.response.AutoMappingResponse;
import cn.orangenode.forge.video.request.SourceRequest;
import cn.orangenode.forge.video.request.MappingRequest;
import cn.orangenode.forge.video.response.SourceResponse;
import cn.orangenode.forge.video.response.SourceCategoryResponse;
import cn.orangenode.forge.video.service.SourceService;
import cn.orangenode.forge.video.support.VideoValues;
import lombok.RequiredArgsConstructor;

/**
 * 资源站与分类映射管理，网络读取不处于数据库事务中。
 */
@Service
@RequiredArgsConstructor
public class SourceServiceImpl implements SourceService {
    private final VideoStore store;
    private final VideoConverter converter;
    private final MacCmsClient client;
    private final MacCmsParser parser;
    private final TransactionTemplate transactions;

    /**
     * 在短事务内匹配缓存分类；创建时按父子顺序导入，不覆盖已有映射。
     */
    @Override
    @Transactional
    public synchronized AutoMappingResponse autoMap(Long id, boolean createMissing) {
        store.settings.selectOne(Wrappers.<cn.orangenode.forge.video.entity.SettingsEntity>lambdaQuery()
                .eq(cn.orangenode.forge.video.entity.SettingsEntity::getId, 1L).last("FOR UPDATE"));
        store.sources.lock(id);
        store.requireSource(id);
        List<SourceCategoryEntity> incoming = store.sourceCategories.selectList(
                Wrappers.<SourceCategoryEntity>lambdaQuery().eq(SourceCategoryEntity::getSourceId, id)
                        .orderByAsc(SourceCategoryEntity::getId));
        if (incoming.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先检测连接并读取分类");
        }
        List<CategoryEntity> local = new ArrayList<>(store.categories.selectList(
                Wrappers.<CategoryEntity>lambdaQuery().orderByAsc(CategoryEntity::getId)));
        int preserved = (int) incoming.stream().filter(e -> e.getCategoryId() != null).count();
        int[] counts = new int[2];
        Set<String> visited = new HashSet<>();
        Set<String> unresolved = new java.util.LinkedHashSet<>();
        for (SourceCategoryEntity item : incoming) {
            autoMapCategory(item, incoming, local, createMissing, visited, unresolved, counts);
        }
        return new AutoMappingResponse(counts[0], counts[1], preserved, List.copyOf(unresolved));
    }

    /**
     * 先匹配父分类，再匹配名称；同名歧义、停用及循环分类保持未绑定。
     */
    private void autoMapCategory(SourceCategoryEntity item, List<SourceCategoryEntity> incoming,
            List<CategoryEntity> local, boolean createMissing, Set<String> visited, Set<String> unresolved, int[] counts) {
        if (item.getCategoryId() != null || unresolved.contains(item.getUpstreamId())) {
            return;
        }
        if (!visited.add(item.getUpstreamId())) {
            unresolved.add(item.getUpstreamId());
            return;
        }
        SourceCategoryEntity parent = incoming.stream()
                .filter(e -> e.getUpstreamId().equals(item.getUpstreamParentId())).findFirst().orElse(null);
        if (parent != null) {
            autoMapCategory(parent, incoming, local, createMissing, visited, unresolved, counts);
            if (unresolved.contains(parent.getUpstreamId())) {
                unresolved.add(item.getUpstreamId());
                return;
            }
        }
        Long parentId = parent == null ? 0L : parent.getCategoryId();
        if ((parent == null && VideoValues.clean(item.getUpstreamParentId()) != null
                && !"0".equals(item.getUpstreamParentId())) || (parentId != null && !store.categoryVisible(parentId))) {
            unresolved.add(item.getUpstreamId());
            return;
        }
        String name = VideoValues.normalize(item.getName());
        List<CategoryEntity> matches = local.stream()
                .filter(e -> VideoValues.normalize(e.getName()).equals(name)).toList();
        if (matches.isEmpty()) {
            String stripped = categoryName(item.getName());
            matches = local.stream().filter(e -> categoryName(e.getName()).equals(stripped)).toList();
        }
        if (matches.size() > 1 && parentId != null) {
            matches = matches.stream().filter(e -> parentId.equals(e.getParentId())).toList();
        }
        CategoryEntity target;
        if (matches.size() == 1 && store.categoryVisible(matches.getFirst().getId())) {
            target = matches.getFirst();
        } else if (matches.isEmpty() && createMissing && name.length() <= 128) {
            target = new CategoryEntity();
            target.setName(item.getName().strip());
            target.setParentId(parentId);
            target.setSortNo(0);
            target.setEnabled(true);
            target.setDeleted(0);
            target.setCreatedAt(VideoValues.now());
            target.setUpdatedAt(VideoValues.now());
            store.categories.insert(target);
            local.add(target);
            counts[0]++;
        } else {
            unresolved.add(item.getUpstreamId());
            return;
        }
        item.setCategoryId(target.getId());
        item.setUpdatedAt(VideoValues.now());
        store.sourceCategories.updateById(item);
        counts[1]++;
    }

    /**
     * 采用参考源码的常见分类后缀规则，仅在唯一候选时绑定。
     */
    private String categoryName(String name) {
        String normalized = VideoValues.normalize(name);
        return normalized.length() > 1 ? normalized.replaceFirst("[片劇剧类類]$", "") : normalized;
    }

    /**
     * 查询来源，包括停用来源。
     */
    @Override
    public List<SourceResponse> list() {
        return store.sources.selectList(Wrappers.<SourceEntity>lambdaQuery().orderByAsc(SourceEntity::getId))
                .stream().map(converter::source).toList();
    }

    /**
     * 保存来源，不改变运行任务。
     */
    @Override
    @Transactional
    public SourceResponse save(Long id, SourceRequest request) {
        client.validateUrl(request.apiUrl());
        try {
            ZoneId.of(request.sourceZone());
        } catch (java.time.DateTimeException failure) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "来源时区不合法");
        }
        SourceEntity e = id == null ? new SourceEntity() : store.requireSource(id);
        if (id == null) {
            e.setCreatedAt(VideoValues.now());
            e.setDeleted(0);
        }
        e.setName(request.name().strip());
        e.setApiUrl(request.apiUrl().strip());
        e.setEnabled(request.enabled());
        e.setSourceZone(request.sourceZone());
        e.setUpdatedAt(VideoValues.now());
        if (id == null) {
            store.sources.insert(e);
        } else {
            store.sources.updateById(e);
        }
        return converter.source(e);
    }

    /**
     * 有来源影片、定时任务或运行引用时阻止删除。
     */
    @Override
    @Transactional
    public void delete(Long id) {
        store.requireSource(id);
        if (store.origins.selectCount(Wrappers.<OriginEntity>lambdaQuery().eq(OriginEntity::getSourceId, id)) > 0
                || store.tasks.selectCount(Wrappers.<TaskEntity>lambdaQuery().eq(TaskEntity::getSourceId, id)) > 0
                || store.runs.selectCount(Wrappers.<RunEntity>lambdaQuery().eq(RunEntity::getSourceId, id)) > 0
                || store.pendings.selectCount(Wrappers.<PendingEntity>lambdaQuery().eq(PendingEntity::getSourceId, id)) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "资源站已被引用，请停用而非删除");
        }
        store.sourceCategories.delete(Wrappers.<SourceCategoryEntity>lambdaQuery().eq(SourceCategoryEntity::getSourceId, id));
        store.sources.deleteById(id);
    }

    /**
     * 读取缓存的来源分类与本地映射。
     */
    @Override
    public List<SourceCategoryResponse> categories(Long id) {
        store.requireSource(id);
        return store.sourceCategories.selectList(Wrappers.<SourceCategoryEntity>lambdaQuery()
                .eq(SourceCategoryEntity::getSourceId, id).orderByAsc(SourceCategoryEntity::getId)).stream()
                .map(e -> new SourceCategoryResponse(e.getUpstreamId(), e.getUpstreamParentId(), e.getName(),
                        VideoValues.textId(e.getCategoryId()))).toList();
    }

    /**
     * 请求完成后以短事务更新分类缓存，不自动按源编号绑定。
     */
    @Override
    public List<SourceCategoryResponse> refresh(Long id) {
        SourceEntity source = store.requireSource(id);
        List<SourceCategoryResponse> incoming = parser.categories(client.get(
                client.url(source.getApiUrl(), "list", null, null, 1)));
        transactions.executeWithoutResult(status -> {
            store.sources.lock(id);
            for (SourceCategoryResponse item : incoming) {
                SourceCategoryEntity e = store.sourceCategories.selectOne(Wrappers.<SourceCategoryEntity>lambdaQuery()
                        .eq(SourceCategoryEntity::getSourceId, id).eq(SourceCategoryEntity::getUpstreamId, item.upstreamId()));
                boolean create = e == null;
                if (create) {
                    e = new SourceCategoryEntity();
                    e.setSourceId(id);
                    e.setUpstreamId(item.upstreamId());
                    e.setCreatedAt(VideoValues.now());
                }
                e.setName(item.name());
                e.setUpstreamParentId(item.upstreamParentId());
                e.setUpdatedAt(VideoValues.now());
                if (create) {
                    store.sourceCategories.insert(e);
                } else {
                    store.sourceCategories.updateById(e);
                }
            }
        });
        return categories(id);
    }

    /**
     * 明确绑定源分类，既有影片分类不随映射修改自动迁移。
     */
    @Override
    @Transactional
    public void map(Long id, MappingRequest request) {
        store.sources.lock(id);
        store.requireSource(id);
        Long categoryId = VideoValues.clean(request.categoryId()) == null ? null : VideoValues.id(request.categoryId());
        if (categoryId != null) {
            store.requireCategory(categoryId);
        }
        SourceCategoryEntity e = store.sourceCategories.selectOne(Wrappers.<SourceCategoryEntity>lambdaQuery()
                .eq(SourceCategoryEntity::getSourceId, id).eq(SourceCategoryEntity::getUpstreamId, request.upstreamId()));
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "请先读取资源站分类");
        }
        e.setCategoryId(categoryId);
        e.setUpdatedAt(VideoValues.now());
        store.sourceCategories.updateById(e);
    }
}
