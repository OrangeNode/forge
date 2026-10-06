package cn.orangenode.forge.video.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import tools.jackson.databind.json.JsonMapper;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.video.collector.CollectedVideo;
import cn.orangenode.forge.video.collector.CollectedLine;
import cn.orangenode.forge.video.collector.CollectedEpisode;
import cn.orangenode.forge.video.collector.CollectedPage;
import cn.orangenode.forge.video.collector.IngestResult;
import cn.orangenode.forge.video.entity.VideoEntity;
import cn.orangenode.forge.video.entity.OriginEntity;
import cn.orangenode.forge.video.entity.SourceCategoryEntity;
import cn.orangenode.forge.video.entity.PlayLineEntity;
import cn.orangenode.forge.video.entity.EpisodeEntity;
import cn.orangenode.forge.video.entity.RunEntity;
import cn.orangenode.forge.video.entity.PendingEntity;
import cn.orangenode.forge.video.request.PendingResolveRequest;
import cn.orangenode.forge.video.response.SettingsResponse;
import cn.orangenode.forge.video.service.IngestService;
import cn.orangenode.forge.video.service.SettingsService;
import cn.orangenode.forge.video.support.VideoValues;
import lombok.RequiredArgsConstructor;

/**
 * 入库以稳定来源标识优先，人工确认与采集共享同一业务规则。
 */
@Service
@RequiredArgsConstructor
public class IngestServiceImpl implements IngestService {
    private final VideoStore store;
    private final SettingsService settingsService;
    private final JsonMapper json;

    /**
     * 显式重新采集待确认快照，允许规则或映射修正后入库，禁止自动解除歧义。
     */
    @Override
    @Transactional
    public synchronized void retryPending(Long pendingId) {
        PendingEntity record = store.requirePending(pendingId);
        store.sources.lock(record.getSourceId());
        record = store.requirePending(pendingId);
        if (!"PENDING".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只可采集尚未处理的待确认数据");
        }
        if (!Boolean.TRUE.equals(store.requireSource(record.getSourceId()).getEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "资源站已停用");
        }
        record.setStatus("RETRYING");
        store.pendings.updateById(record);
        CollectedVideo data = json.readValue(record.getPayload(), CollectedVideo.class);
        IngestResult result = ingest(record.getSourceId(), data, settingsService.get(), record.getRunId(), null, null);
        if (result.inserted() + result.updated() == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, result.pending() > 0
                    ? store.requirePending(pendingId).getReason() : "当前缺失信息策略要求跳过该数据");
        }
        record.setStatus("RESOLVED");
        record.setUpdatedAt(VideoValues.now());
        store.pendings.updateById(record);
    }

    /**
     * 页面全部入库成功后才移动断点；重放已提交页面不会重复统计。
     */
    @Override
    @Transactional
    public synchronized void page(Long runId, CollectedPage page) {
        RunEntity run = store.requireRun(runId);
        if (page.page() < run.getNextPage()) {
            return;
        }
        if (page.page() != run.getNextPage()
                || !List.of("RUNNING", "STOP_REQUESTED").contains(run.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "运行状态或页面断点不匹配");
        }
        SettingsResponse rules = json.readValue(run.getSettingsSnapshot(), SettingsResponse.class);
        for (CollectedVideo video : page.videos()) {
            IngestResult result = ingest(run.getSourceId(), video, rules, runId, null, null);
            run.setProcessed(run.getProcessed() + 1);
            run.setInserted(run.getInserted() + result.inserted());
            run.setUpdated(run.getUpdated() + result.updated());
            run.setPending(run.getPending() + result.pending());
            run.setSkipped(run.getSkipped() + result.skipped());
        }
        run.setNextPage(page.page() + 1);
        run.setPageCount(page.pageCount());
        run.setUpdatedAt(VideoValues.now());
        // 不改状态，避免覆盖页面处理期间管理员提交的停止请求。
        store.runs.update(Wrappers.<RunEntity>lambdaUpdate().eq(RunEntity::getId, runId)
                .set(RunEntity::getNextPage, run.getNextPage()).set(RunEntity::getPageCount, run.getPageCount())
                .set(RunEntity::getProcessed, run.getProcessed()).set(RunEntity::getInserted, run.getInserted())
                .set(RunEntity::getUpdated, run.getUpdated()).set(RunEntity::getPending, run.getPending())
                .set(RunEntity::getSkipped, run.getSkipped()).set(RunEntity::getUpdatedAt, run.getUpdatedAt()));
    }

    /**
     * 确认时再次检查来源关联，禁止重复或将已关联来源移动到其他影片。
     */
    @Override
    @Transactional
    public synchronized void resolve(Long pendingId, PendingResolveRequest request) {
        PendingEntity pending = store.requirePending(pendingId);
        store.sources.lock(pending.getSourceId());
        pending = store.requirePending(pendingId);
        if (!"PENDING".equals(pending.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该数据已经处理");
        }
        if ("IGNORE".equals(request.action())) {
            pending.setStatus("IGNORED");
        } else {
            CollectedVideo video = json.readValue(pending.getPayload(), CollectedVideo.class);
            if (VideoValues.clean(video.name()) == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少名称的数据暂不能入库，请重新采集");
            }
            Long target = "BIND".equals(request.action()) ? VideoValues.id(request.videoId()) : null;
            if (target != null) {
                store.requireVideo(target);
            }
            Long category = request.categoryId() == null || request.categoryId().isBlank()
                    ? mappedCategory(pending.getSourceId(), video.upstreamCategoryId())
                    : VideoValues.id(request.categoryId());
            if (target == null && (category == null || !store.categoryVisible(category))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "新建影片需要选择有效的本地分类");
            }
            if (video.lines().isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无播放线路的数据暂不能入库，请重新采集");
            }
            OriginEntity existing = store.findOrigin(pending.getSourceId(), video.upstreamId());
            if (existing != null) {
                throw new BusinessException(ErrorCode.CONFLICT, "该来源影片已经关联，请刷新记录");
            }
            ingest(pending.getSourceId(), video, settingsService.get(), pending.getRunId(), target, category);
            pending.setStatus("RESOLVED");
        }
        pending.setUpdatedAt(VideoValues.now());
        store.pendings.updateById(pending);
    }

    /**
     * 源分类通过显式映射转为本地分类，源编号不跨站比较。
     */
    private Long mappedCategory(Long source, String upstream) {
        if (upstream == null) {
            return null;
        }
        SourceCategoryEntity mapping = store.sourceCategories.selectOne(Wrappers.<SourceCategoryEntity>lambdaQuery()
                .eq(SourceCategoryEntity::getSourceId, source).eq(SourceCategoryEntity::getUpstreamId, upstream));
        return mapping == null ? null : mapping.getCategoryId();
    }

    /**
     * 单条来源先稳定定位，再进行严格条件匹配；不确定时不改影片和地址。
     */
    private IngestResult ingest(Long sourceId, CollectedVideo data, SettingsResponse rules, Long runId,
            Long forcedVideo, Long forcedCategory) {
        store.sources.lock(sourceId);
        OriginEntity origin = store.findOrigin(sourceId, data.upstreamId());
        if (origin == null && forcedVideo == null && forcedCategory == null
                && store.pendings.selectCount(Wrappers.<PendingEntity>lambdaQuery()
                        .eq(PendingEntity::getSourceId, sourceId).eq(PendingEntity::getUpstreamId, data.upstreamId())
                        .in(PendingEntity::getStatus, List.of("IGNORED", "DELETED"))) > 0) {
            return new IngestResult(0, 0, 0, 1);
        }
        VideoEntity video = null;
        boolean newVideo = false;
        Long category = forcedCategory == null ? mappedCategory(sourceId, data.upstreamCategoryId()) : forcedCategory;
        if (forcedVideo == null && forcedCategory == null) {
            IngestResult missing = missingInformation(sourceId, data, rules, runId, category, origin != null);
            if (missing != null) {
                return missing;
            }
        }
        if (origin != null) {
            video = store.videos.selectOne(Wrappers.<VideoEntity>lambdaQuery()
                    .eq(VideoEntity::getId, origin.getVideoId()).last("FOR UPDATE"));
            if (video == null) {
                return new IngestResult(0, 0, 0, 1);
            }
        } else if (forcedVideo != null) {
            video = store.lockVideo(forcedVideo);
        } else if (forcedCategory == null) {
            if (category == null || !store.categoryVisible(category)) {
                return pending(sourceId, data, runId, "分类尚未映射或已停用", List.of());
            }
            List<VideoEntity> candidates = store.videos.selectList(Wrappers.<VideoEntity>lambdaQuery()
                    .eq(VideoEntity::getMatchName, VideoValues.normalize(data.name())));
            boolean incomplete = rules.matchFields().stream().anyMatch(f -> matchValue(f, data, category).isEmpty());
            List<VideoEntity> possible = incomplete ? List.of() : candidates.stream().filter(e -> rules.matchFields().stream()
                    .allMatch(f -> matchValue(f, e).isEmpty()
                            || matchValue(f, e).equals(matchValue(f, data, category)))).toList();
            if (possible.size() > 1 || (possible.size() == 1 && rules.matchFields().stream()
                    .anyMatch(f -> matchValue(f, possible.getFirst()).isEmpty()))) {
                return pending(sourceId, data, runId, "匹配多部影片或已有影片的匹配资料不完整", possible);
            }
            PendingEntity earlier = store.pendings.selectOne(Wrappers.<PendingEntity>lambdaQuery()
                    .eq(PendingEntity::getSourceId, sourceId).eq(PendingEntity::getUpstreamId, data.upstreamId()));
            if (earlier != null && "PENDING".equals(earlier.getStatus())) {
                return pending(sourceId, data, runId, "曾进入待确认，请管理员确认最新来源资料", possible);
            }
            if (possible.size() == 1) {
                video = store.lockVideo(possible.getFirst().getId());
                VideoEntity locked = video;
                if (!rules.matchFields().stream().allMatch(f -> matchValue(f, locked).equals(matchValue(f, data, category)))) {
                    return pending(sourceId, data, runId, "候选影片资料已变化，请重新确认", List.of(video));
                }
            }
        }
        if (video == null) {
            if (data.lines().isEmpty()) {
                return pending(sourceId, data, runId, "来源尚未返回非空播放线路", List.of());
            }
            newVideo = true;
            video = new VideoEntity();
            video.setName(data.name());
            video.setMatchName(VideoValues.normalize(data.name()));
            video.setCategoryId(category);
            video.setMetadataLocked(false);
            video.setPublished(rules.autoPublish() && store.categoryVisible(category));
            video.setDeleted(0);
            video.setCreatedAt(VideoValues.now());
            video.setUpdatedAt(VideoValues.now());
            updateMetadata(video, data, category, new SettingsResponse(rules.matchFields(),
                    List.of("name", "category", "year", "area", "language", "director", "actors", "posterUrl",
                            "description", "remarks", "declaredTotal", "ended"), rules.autoPublish()));
            store.videos.insert(video);
        }
        if (origin == null) {
            origin = new OriginEntity();
            origin.setSourceId(sourceId);
            origin.setUpstreamId(data.upstreamId());
            origin.setVideoId(video.getId());
            origin.setCreatedAt(VideoValues.now());
        }
        origin.setUpstreamCategoryId(data.upstreamCategoryId());
        if (data.name() != null) {
            origin.setName(data.name());
        }
        origin.setYear(data.year());
        origin.setArea(data.area());
        origin.setLanguage(data.language());
        origin.setDirector(data.director());
        origin.setActors(data.actors());
        origin.setSourceUpdatedAt(data.sourceUpdatedAt());
        origin.setCollectedAt(VideoValues.now());
        origin.setUpdatedAt(VideoValues.now());
        if (origin.getId() == null) {
            store.origins.insert(origin);
        } else {
            store.origins.updateById(origin);
        }
        if (newVideo) {
            video.setPrimaryOriginId(origin.getId());
        } else if (origin.getId().equals(video.getPrimaryOriginId())
                && !Boolean.TRUE.equals(video.getMetadataLocked())) {
            updateMetadata(video, data, category, rules);
        }
        video.setUpdatedAt(VideoValues.now());
        store.videos.updateById(video);
        replaceLines(origin.getId(), data.lines());
        return new IngestResult(newVideo ? 1 : 0, newVideo ? 0 : 1, 0, 0);
    }

    /**
     * 已忽略的数据不自动恢复，待确认数据更新最新快照。
     */
    private IngestResult pending(Long sourceId, CollectedVideo data, Long runId, String reason, List<VideoEntity> candidates) {
        PendingEntity e = store.pendings.selectOne(Wrappers.<PendingEntity>lambdaQuery()
                .eq(PendingEntity::getSourceId, sourceId).eq(PendingEntity::getUpstreamId, data.upstreamId()));
        if (e != null && List.of("IGNORED", "DELETED").contains(e.getStatus())) {
            return new IngestResult(0, 0, 0, 1);
        }
        boolean create = e == null;
        if (create) {
            e = new PendingEntity();
            e.setSourceId(sourceId);
            e.setUpstreamId(data.upstreamId());
            e.setCreatedAt(VideoValues.now());
        }
        e.setRunId(runId);
        e.setPayload(json.writeValueAsString(data));
        e.setCandidateIds(String.join(",", candidates.stream().map(v -> v.getId().toString()).toList()));
        e.setReason(reason);
        e.setStatus("PENDING");
        e.setUpdatedAt(VideoValues.now());
        if (create) {
            store.pendings.insert(e);
        } else {
            store.pendings.updateById(e);
        }
        return new IngestResult(0, 0, 1, 0);
    }

    /**
     * 将存疑原因中的去重字段显示为中文业务名称。
     */
    private String matchLabel(String field) {
        return switch (field) {
            case "name" -> "名称";
            case "year" -> "年份";
            case "category" -> "本地分类";
            case "director" -> "导演";
            case "actors" -> "演员";
            case "area" -> "地区";
            case "language" -> "语言";
            case "posterUrl" -> "封面";
            case "description" -> "简介";
            case "playUrls" -> "播放地址";
            default -> "必要资料";
        };
    }

    /**
     * 按字段策略处理缺失资料；已关联来源默认保留旧资料与线路，显式设置仍生效。
     */
    private IngestResult missingInformation(Long sourceId, CollectedVideo data, SettingsResponse rules,
            Long runId, Long category, boolean existing) {
        java.util.List<String> pendingFields = new java.util.ArrayList<>();
        for (String field : List.of("name", "category", "year", "area", "language", "director", "actors",
                "posterUrl", "description", "playUrls")) {
            boolean missing = switch (field) {
                case "category" -> category == null || !store.categoryVisible(category);
                case "posterUrl" -> VideoValues.clean(data.posterUrl()) == null;
                case "description" -> VideoValues.clean(data.description()) == null;
                case "playUrls" -> data.lines().stream().allMatch(l -> l.episodes().isEmpty());
                default -> matchValue(field, data, category).isEmpty();
            };
            if (!missing || (existing && !"name".equals(field)
                    && (rules.missingPolicies() == null || !rules.missingPolicies().containsKey(field)))) {
                continue;
            }
            String policy = rules.missingPolicy(field);
            if ("SKIP".equals(policy)) {
                return new IngestResult(0, 0, 0, 1);
            }
            if ("PENDING".equals(policy) || List.of("name", "category", "playUrls").contains(field)) {
                pendingFields.add(matchLabel(field));
            }
        }
        return pendingFields.isEmpty() ? null : pending(sourceId, data, runId,
                "缺失信息或分类不可用：" + String.join("、", pendingFields), List.of());
    }

    /**
     * 从采集快照取得规范化条件值。
     */
    private String matchValue(String field, CollectedVideo v, Long category) {
        return switch (field) {
            case "name" -> VideoValues.normalize(v.name());
            case "year" -> v.year() == null ? "" : v.year().toString();
            case "category" -> category == null ? "" : category.toString();
            case "director" -> VideoValues.people(v.director());
            case "actors" -> VideoValues.people(v.actors());
            case "area" -> VideoValues.normalize(v.area());
            case "language" -> VideoValues.normalize(v.language());
            default -> "";
        };
    }

    /**
     * 从已有影片取得规范化条件值。
     */
    private String matchValue(String field, VideoEntity v) {
        return switch (field) {
            case "name" -> VideoValues.normalize(v.getName());
            case "year" -> v.getYear() == null ? "" : v.getYear().toString();
            case "category" -> VideoValues.textId(v.getCategoryId());
            case "director" -> VideoValues.people(v.getDirector());
            case "actors" -> VideoValues.people(v.getActors());
            case "area" -> VideoValues.normalize(v.getArea());
            case "language" -> VideoValues.normalize(v.getLanguage());
            default -> "";
        };
    }

    /**
     * 只由主要来源覆盖选中的非空资料，不改管理员发布和锁定状态。
     */
    private void updateMetadata(VideoEntity e, CollectedVideo v, Long category, SettingsResponse rules) {
        List<String> fields = rules.updateFields();
        if (fields.contains("name") && v.name() != null) {
            e.setName(v.name());
            e.setMatchName(VideoValues.normalize(v.name()));
        }
        if (fields.contains("category") && category != null && store.categoryVisible(category)) {
            e.setCategoryId(category);
        }
        if (fields.contains("year") && v.year() != null) {
            e.setYear(v.year());
        }
        if (fields.contains("area") && v.area() != null) {
            e.setArea(v.area());
        }
        if (fields.contains("language") && v.language() != null) {
            e.setLanguage(v.language());
        }
        if (fields.contains("director") && v.director() != null) {
            e.setDirector(v.director());
        }
        if (fields.contains("actors") && v.actors() != null) {
            e.setActors(v.actors());
        }
        if (fields.contains("posterUrl") && v.posterUrl() != null) {
            e.setPosterUrl(v.posterUrl());
        }
        if (fields.contains("description") && v.description() != null) {
            e.setDescription(v.description());
        }
        if (fields.contains("remarks") && v.remarks() != null) {
            e.setRemarks(v.remarks());
        }
        if (fields.contains("declaredTotal") && v.declaredTotal() != null) {
            e.setDeclaredTotal(v.declaredTotal());
        }
        if (fields.contains("ended") && v.ended() != null) {
            e.setEnded(v.ended());
        }
    }

    /**
     * 只替换成功返回的非空线路，保留其他来源、未返回线路及停用状态。
     */
    private void replaceLines(Long originId, List<CollectedLine> lines) {
        int lineOrder = 0;
        for (CollectedLine incoming : lines) {
            if (incoming.episodes().isEmpty()) {
                continue;
            }
            PlayLineEntity line = store.playLines.selectOne(Wrappers.<PlayLineEntity>lambdaQuery()
                    .eq(PlayLineEntity::getOriginId, originId).eq(PlayLineEntity::getCode, incoming.code()));
            if (line == null) {
                line = new PlayLineEntity();
                line.setOriginId(originId);
                line.setCode(incoming.code());
                line.setName(incoming.code());
                line.setSortNo(lineOrder);
                line.setEnabled(true);
                line.setCreatedAt(VideoValues.now());
                line.setUpdatedAt(VideoValues.now());
                store.playLines.insert(line);
            }
            store.episodes.delete(Wrappers.<EpisodeEntity>lambdaQuery().eq(EpisodeEntity::getLineId, line.getId()));
            int index = 0;
            for (CollectedEpisode item : incoming.episodes()) {
                EpisodeEntity e = new EpisodeEntity();
                e.setLineId(line.getId());
                e.setName(item.name());
                e.setSortNo(index++);
                e.setUrl(item.url());
                e.setUrlType(item.urlType());
                e.setCreatedAt(VideoValues.now());
                e.setUpdatedAt(VideoValues.now());
                store.episodes.insert(e);
            }
            line.setUpdatedAt(VideoValues.now());
            store.playLines.updateById(line);
            lineOrder++;
        }
    }
}
