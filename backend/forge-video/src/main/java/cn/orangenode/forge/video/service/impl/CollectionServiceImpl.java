package cn.orangenode.forge.video.service.impl;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import tools.jackson.databind.json.JsonMapper;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.video.collector.MacCmsClient;
import cn.orangenode.forge.video.collector.TaskSchedules;
import cn.orangenode.forge.video.collector.CollectedVideo;
import cn.orangenode.forge.video.config.VideoProperties;
import cn.orangenode.forge.video.converter.VideoConverter;
import cn.orangenode.forge.video.entity.TaskEntity;
import cn.orangenode.forge.video.entity.RunEntity;
import cn.orangenode.forge.video.entity.PendingEntity;
import cn.orangenode.forge.video.entity.SourceEntity;
import cn.orangenode.forge.video.entity.SourceCategoryEntity;
import cn.orangenode.forge.video.entity.VideoEntity;
import cn.orangenode.forge.video.request.TaskRequest;
import cn.orangenode.forge.video.request.CollectRequest;
import cn.orangenode.forge.video.response.TaskResponse;
import cn.orangenode.forge.video.response.RunResponse;
import cn.orangenode.forge.video.response.PendingResponse;
import cn.orangenode.forge.video.response.PendingVideoResponse;
import cn.orangenode.forge.video.response.VideoResponse;
import cn.orangenode.forge.video.service.CollectionService;
import cn.orangenode.forge.video.service.SettingsService;
import cn.orangenode.forge.video.support.VideoPaging;
import cn.orangenode.forge.video.support.VideoValues;
import lombok.RequiredArgsConstructor;

/**
 * 单实例采集运行队列，后台提交不等待网络与采集完成。
 */
@Service
@RequiredArgsConstructor
public class CollectionServiceImpl implements CollectionService {
    private final VideoStore store;
    private final VideoConverter converter;
    private final VideoPaging paging;
    private final SettingsService settings;
    private final MacCmsClient client;
    private final JsonMapper json;
    private final VideoProperties properties;

    /**
     * 查询全部有效任务。
     */
    @Override
    public List<TaskResponse> tasks() {
        return store.tasks.selectList(Wrappers.<TaskEntity>lambdaQuery().orderByAsc(TaskEntity::getId))
                .stream().map(converter::task).toList();
    }

    /**
     * 校验运行频率和时区，新建任务一律停用，启用时检查分类映射。
     */
    @Override
    @Transactional
    public synchronized TaskResponse saveTask(Long id, TaskRequest r) {
        store.requireSource(VideoValues.id(r.sourceId()));
        try {
            ZoneId.of(r.zoneId());
            LocalTime.parse(r.timeOfDay());
        } catch (java.time.DateTimeException failure) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务时间或时区不合法");
        }
        if (("INTERVAL".equals(r.frequency()) && r.intervalHours() == null)
                || ("WEEKLY".equals(r.frequency()) && r.weekday() == null)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "按小时执行需要间隔，每周执行需要星期");
        }
        TaskEntity e = id == null ? new TaskEntity() : store.requireTask(id);
        if (id == null) {
            e.setCreatedAt(VideoValues.now());
            e.setDeleted(0);
        }
        e.setName(r.name().strip());
        e.setSourceId(VideoValues.id(r.sourceId()));
        e.setRangeCode(r.rangeCode());
        e.setUpstreamCategoryId(VideoValues.clean(r.upstreamCategoryId()));
        e.setFrequency(r.frequency());
        e.setIntervalHours(r.intervalHours());
        e.setWeekday(r.weekday());
        e.setTimeOfDay(r.timeOfDay());
        e.setZoneId(r.zoneId());
        e.setEnabled(id != null && r.enabled());
        if (Boolean.TRUE.equals(e.getEnabled())) {
            SourceEntity source = store.requireSource(e.getSourceId());
            if (!Boolean.TRUE.equals(source.getEnabled()) || store.sourceCategories.selectCount(
                    Wrappers.<SourceCategoryEntity>lambdaQuery().eq(SourceCategoryEntity::getSourceId, e.getSourceId())
                            .eq(e.getUpstreamCategoryId() != null, SourceCategoryEntity::getUpstreamId, e.getUpstreamCategoryId())
                            .isNotNull(SourceCategoryEntity::getCategoryId)) == 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "启用任务前请启用来源并配置分类映射");
            }
        }
        e.setNextRunAt(TaskSchedules.next(e, VideoValues.now()));
        e.setUpdatedAt(VideoValues.now());
        if (id == null) {
            store.tasks.insert(e);
        } else {
            store.tasks.updateById(e);
        }
        return converter.task(e);
    }

    /**
     * 活动运行引用的任务不能删除。
     */
    @Override
    @Transactional
    public synchronized void deleteTask(Long id) {
        store.requireTask(id);
        if (store.runs.selectCount(Wrappers.<RunEntity>lambdaQuery().eq(RunEntity::getTaskId, id)
                .in(RunEntity::getStatus, List.of("QUEUED", "RUNNING", "STOP_REQUESTED"))) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务有活动运行，请先停止");
        }
        store.tasks.deleteById(id);
    }

    /**
     * 提交手动运行并保存规则快照。
     */
    @Override
    @Transactional
    public synchronized RunResponse collect(CollectRequest r) {
        return converter.run(enqueue(VideoValues.id(r.sourceId()), r.rangeCode(), r.upstreamCategoryId(), null));
    }

    /**
     * 校验来源可用并防止同来源的重叠运行。
     */
    private RunEntity enqueue(Long sourceId, String range, String category, Long taskId) {
        SourceEntity source = store.sources.lock(sourceId);
        if (source == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "资源站不存在");
        }
        if (!Boolean.TRUE.equals(source.getEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "资源站已停用");
        }
        client.url(source.getApiUrl(), "detail", range, category, 1);
        if (store.runs.selectCount(Wrappers.<RunEntity>lambdaQuery().eq(RunEntity::getSourceId, sourceId)
                .in(RunEntity::getStatus, List.of("QUEUED", "RUNNING", "STOP_REQUESTED"))) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该来源已有排队或运行中的采集");
        }
        RunEntity e = new RunEntity();
        e.setSourceId(sourceId);
        e.setTaskId(taskId);
        e.setRangeCode(range);
        e.setUpstreamCategoryId(VideoValues.clean(category));
        e.setStatus("QUEUED");
        e.setNextPage(1);
        e.setPageCount(0);
        e.setProcessed(0L);
        e.setInserted(0L);
        e.setUpdated(0L);
        e.setPending(0L);
        e.setSkipped(0L);
        e.setSettingsSnapshot(json.writeValueAsString(settings.get()));
        e.setCreatedAt(VideoValues.now());
        e.setUpdatedAt(VideoValues.now());
        store.runs.insert(e);
        return e;
    }

    /**
     * 预览只生成链接，不发请求。
     */
    @Override
    public String preview(CollectRequest r) {
        SourceEntity source = store.requireSource(VideoValues.id(r.sourceId()));
        return client.url(source.getApiUrl(), "detail", r.rangeCode(), r.upstreamCategoryId(), 1);
    }

    /**
     * 查询运行历史与进度。
     */
    @Override
    public PageResponse<RunResponse> runs(Integer number, Integer size, String status) {
        Page<RunEntity> p = store.runs.selectPage(paging.page(number, size),
                Wrappers.<RunEntity>lambdaQuery().eq(VideoValues.clean(status) != null, RunEntity::getStatus, status)
                        .orderByDesc(RunEntity::getId));
        return new PageResponse<>(p.getRecords().stream().map(converter::run).toList(),
                p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    /**
     * 从数据库读取进度，不读取工作线程状态。
     */
    @Override
    public RunResponse run(Long id) {
        return converter.run(store.requireRun(id));
    }

    /**
     * 排队任务直接停止，运行任务在当前页提交后停止。
     */
    @Override
    @Transactional
    public synchronized void stop(Long id) {
        RunEntity e = store.requireRun(id);
        if ("QUEUED".equals(e.getStatus())) {
            e.setStatus("STOPPED");
            e.setFinishedAt(VideoValues.now());
        } else if ("RUNNING".equals(e.getStatus())) {
            e.setStatus("STOP_REQUESTED");
        } else if (!"STOP_REQUESTED".equals(e.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该运行不能停止");
        }
        store.runs.update(Wrappers.<RunEntity>lambdaUpdate().eq(RunEntity::getId, id)
                .in(RunEntity::getStatus, List.of("QUEUED", "RUNNING", "STOP_REQUESTED"))
                .set(RunEntity::getStatus, e.getStatus()).set(RunEntity::getFinishedAt, e.getFinishedAt())
                .set(RunEntity::getUpdatedAt, VideoValues.now()));
    }

    /**
     * 保留旧统计与规则快照，超过滚动窗口时提示扩大范围补采。
     */
    @Override
    @Transactional
    public synchronized void resume(Long id) {
        RunEntity e = store.requireRun(id);
        store.sources.lock(e.getSourceId());
        if (!List.of("FAILED", "STOPPED", "INTERRUPTED").contains(e.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有失败、停止或中断运行可以继续");
        }
        if (!Boolean.TRUE.equals(store.requireSource(e.getSourceId()).getEnabled())
                || store.runs.selectCount(Wrappers.<RunEntity>lambdaQuery().eq(RunEntity::getSourceId, e.getSourceId())
                        .in(RunEntity::getStatus, List.of("QUEUED", "RUNNING", "STOP_REQUESTED"))) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "来源已停用或已有活动采集");
        }
        int hours = "DAY".equals(e.getRangeCode()) ? 24 : "WEEK".equals(e.getRangeCode()) ? 168 : 0;
        if (hours > 0 && e.getUpdatedAt().isBefore(VideoValues.now().minusHours(hours))) {
            e.setWarning("已超过本次滚动采集窗口，请另做周采集或全量补采；断点不是源站数据快照");
        }
        store.runs.update(Wrappers.<RunEntity>lambdaUpdate().eq(RunEntity::getId, id)
                .set(RunEntity::getStatus, "QUEUED").set(RunEntity::getErrorMessage, null)
                .set(RunEntity::getFinishedAt, null).set(RunEntity::getWarning, e.getWarning())
                .set(RunEntity::getUpdatedAt, VideoValues.now()));
    }

    /**
     * 返回存疑影片的候选资料，避免泄漏整个来源原始返回体。
     */
    @Override
    public PageResponse<PendingResponse> pending(Integer number, Integer size, String status, String runId) {
        Page<PendingEntity> p = store.pendings.selectPage(paging.page(number, size),
                Wrappers.<PendingEntity>lambdaQuery()
                        .ne(PendingEntity::getStatus, "DELETED")
                        .eq(VideoValues.clean(status) != null, PendingEntity::getStatus, status)
                        .eq(VideoValues.clean(runId) != null, PendingEntity::getRunId,
                                VideoValues.clean(runId) == null ? null : VideoValues.id(runId))
                        .orderByDesc(PendingEntity::getId));
        List<PendingResponse> records = p.getRecords().stream().map(e -> {
            CollectedVideo v = json.readValue(e.getPayload(), CollectedVideo.class);
            List<VideoResponse> candidates = VideoValues.fields(e.getCandidateIds()).stream()
                    .map(VideoValues::id).map(store.videos::selectById).filter(java.util.Objects::nonNull)
                    .map(converter::video).toList();
            return new PendingResponse(e.getId().toString(), VideoValues.textId(e.getRunId()),
                    e.getSourceId().toString(), e.getUpstreamId(), v.name(), e.getReason(), e.getStatus(), candidates,
                    new PendingVideoResponse(v.name(), v.upstreamCategoryId(), v.year(), v.area(), v.language(),
                            v.director(), v.actors(), v.posterUrl(), v.description(), v.remarks(), v.declaredTotal(),
                            v.ended(), v.lines().size(), v.lines().stream().mapToInt(l -> l.episodes().size()).sum()));
        }).toList();
        return new PageResponse<>(records, p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    /**
     * 对每个到期任务最多排队一次，来源忙碌或停用时记录失败而不阻塞其他任务。
     */
    @Override
    @Transactional
    public synchronized void dispatchDue() {
        LocalDateTime now = VideoValues.now();
        for (TaskEntity task : store.tasks.selectList(Wrappers.<TaskEntity>lambdaQuery()
                .eq(TaskEntity::getEnabled, true).le(TaskEntity::getNextRunAt, now).orderByAsc(TaskEntity::getId))) {
            try {
                RunEntity run = enqueue(task.getSourceId(), task.getRangeCode(), task.getUpstreamCategoryId(), task.getId());
                int hours = "DAY".equals(task.getRangeCode()) ? 24 : "WEEK".equals(task.getRangeCode()) ? 168 : 0;
                if (hours > 0 && task.getNextRunAt().isBefore(now.minusHours(hours))) {
                    run.setWarning("停机时间超过采集窗口，请使用周或全量补采");
                    store.runs.updateById(run);
                }
            } catch (BusinessException failure) {
                RunEntity failed = new RunEntity();
                failed.setSourceId(task.getSourceId());
                failed.setTaskId(task.getId());
                failed.setRangeCode(task.getRangeCode());
                failed.setUpstreamCategoryId(task.getUpstreamCategoryId());
                failed.setStatus("FAILED");
                failed.setNextPage(1);
                failed.setPageCount(0);
                failed.setProcessed(0L);
                failed.setInserted(0L);
                failed.setUpdated(0L);
                failed.setPending(0L);
                failed.setSkipped(0L);
                failed.setErrorMessage(failure.getMessage());
                failed.setCreatedAt(now);
                failed.setUpdatedAt(now);
                failed.setFinishedAt(now);
                failed.setSettingsSnapshot(json.writeValueAsString(settings.get()));
                store.runs.insert(failed);
            }
            task.setNextRunAt(TaskSchedules.next(task, now));
            task.setUpdatedAt(now);
            store.tasks.updateById(task);
        }
    }

    /**
     * 由唯一执行器取得最早排队的任务。
     */
    @Override
    @Transactional
    public synchronized Long claim() {
        List<RunEntity> queued = store.runs.selectList(Wrappers.<RunEntity>lambdaQuery()
                .eq(RunEntity::getStatus, "QUEUED").orderByAsc(RunEntity::getId).last("LIMIT 1"));
        if (queued.isEmpty()) {
            return null;
        }
        RunEntity e = queued.getFirst();
        e.setStatus("RUNNING");
        if (e.getStartedAt() == null) {
            e.setStartedAt(VideoValues.now());
        }
        e.setUpdatedAt(VideoValues.now());
        int claimed = store.runs.update(Wrappers.<RunEntity>lambdaUpdate().eq(RunEntity::getId, e.getId())
                .eq(RunEntity::getStatus, "QUEUED").set(RunEntity::getStatus, "RUNNING")
                .set(RunEntity::getStartedAt, e.getStartedAt()).set(RunEntity::getUpdatedAt, e.getUpdatedAt()));
        return claimed == 1 ? e.getId() : null;
    }

    /**
     * 只提交运行终态，不修改已提交的页面计数。
     */
    @Override
    @Transactional
    public synchronized void finish(Long id, String status, String error) {
        RunEntity e = store.requireRun(id);
        store.runs.update(Wrappers.<RunEntity>lambdaUpdate().eq(RunEntity::getId, id)
                .set(RunEntity::getStatus, "STOP_REQUESTED".equals(e.getStatus()) ? "STOPPED" : status)
                .set(RunEntity::getErrorMessage, error).set(RunEntity::getFinishedAt, VideoValues.now())
                .set(RunEntity::getUpdatedAt, VideoValues.now()));
    }

    /**
     * 重启时不自动恢复网络任务，保持已经提交的页面断点。
     */
    @Override
    @Transactional
    public void recover() {
        store.runs.update(Wrappers.<RunEntity>lambdaUpdate()
                .in(RunEntity::getStatus, List.of("RUNNING", "STOP_REQUESTED"))
                .set(RunEntity::getStatus, "INTERRUPTED").set(RunEntity::getUpdatedAt, VideoValues.now())
                .set(RunEntity::getErrorMessage, "服务重启，运行已中断，可从断点继续"));
    }

    /**
     * 保留未处理待确认及活动运行，只清理已结束且无待确认引用的旧运行。
     */
    @Override
    @Transactional
    public void cleanup() {
        LocalDateTime boundary = VideoValues.now().minusDays(properties.getLogRetentionDays());
        for (RunEntity e : store.runs.selectList(Wrappers.<RunEntity>lambdaQuery()
                .in(RunEntity::getStatus, List.of("COMPLETED", "FAILED", "STOPPED", "INTERRUPTED"))
                .lt(RunEntity::getUpdatedAt, boundary))) {
            if (store.pendings.selectCount(Wrappers.<PendingEntity>lambdaQuery().eq(PendingEntity::getRunId, e.getId())
                    .eq(PendingEntity::getStatus, "PENDING")) == 0) {
                store.pendings.update(Wrappers.<PendingEntity>lambdaUpdate().eq(PendingEntity::getRunId, e.getId())
                        .in(PendingEntity::getStatus, List.of("IGNORED", "DELETED")).set(PendingEntity::getRunId, null));
                store.pendings.delete(Wrappers.<PendingEntity>lambdaQuery().eq(PendingEntity::getRunId, e.getId())
                        .eq(PendingEntity::getStatus, "RESOLVED"));
                store.runs.deleteById(e.getId());
            }
        }
    }
}
