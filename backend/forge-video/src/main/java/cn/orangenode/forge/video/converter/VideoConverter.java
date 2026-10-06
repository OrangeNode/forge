package cn.orangenode.forge.video.converter;

import org.springframework.stereotype.Component;
import cn.orangenode.forge.video.entity.SourceEntity;
import cn.orangenode.forge.video.entity.VideoEntity;
import cn.orangenode.forge.video.entity.OriginEntity;
import cn.orangenode.forge.video.entity.EpisodeEntity;
import cn.orangenode.forge.video.entity.TaskEntity;
import cn.orangenode.forge.video.entity.RunEntity;
import cn.orangenode.forge.video.response.SourceResponse;
import cn.orangenode.forge.video.response.VideoResponse;
import cn.orangenode.forge.video.response.OriginResponse;
import cn.orangenode.forge.video.response.EpisodeResponse;
import cn.orangenode.forge.video.response.TaskResponse;
import cn.orangenode.forge.video.response.RunResponse;
import cn.orangenode.forge.video.response.AppVideoResponse;
import cn.orangenode.forge.video.support.VideoValues;

/**
 * 视频模块显式字段转换，不通过反射复制。
 */
@Component
public class VideoConverter {
    /**
     * 转换资源站资料。
     */
    public SourceResponse source(SourceEntity e) {
        return new SourceResponse(VideoValues.textId(e.getId()), e.getName(), e.getApiUrl(),
                Boolean.TRUE.equals(e.getEnabled()), e.getSourceZone());
    }
    /**
     * 转换影片后台资料。
     */
    public VideoResponse video(VideoEntity e) {
        return new VideoResponse(VideoValues.textId(e.getId()), e.getName(), VideoValues.textId(e.getCategoryId()),
                e.getYear(), e.getArea(), e.getLanguage(), e.getDirector(), e.getActors(), e.getPosterUrl(),
                e.getDescription(), e.getRemarks(), e.getDeclaredTotal(), e.getEnded(),
                Boolean.TRUE.equals(e.getPublished()), Boolean.TRUE.equals(e.getMetadataLocked()),
                VideoValues.textId(e.getPrimaryOriginId()), VideoValues.time(e.getCreatedAt()),
                VideoValues.time(e.getUpdatedAt()));
    }
    /**
     * 转换公开影片资料，不泄漏内部管理字段。
     */
    public AppVideoResponse appVideo(VideoEntity e) {
        return new AppVideoResponse(VideoValues.textId(e.getId()), e.getName(), VideoValues.textId(e.getCategoryId()),
                e.getYear(), e.getArea(), e.getLanguage(), e.getDirector(), e.getActors(), e.getPosterUrl(),
                e.getDescription(), e.getRemarks(), e.getDeclaredTotal(), e.getEnded(), VideoValues.time(e.getUpdatedAt()));
    }
    /**
     * 转换分集地址。
     */
    public EpisodeResponse episode(EpisodeEntity e) {
        return new EpisodeResponse(VideoValues.textId(e.getId()), e.getName(), e.getSortNo(), e.getUrl(), e.getUrlType());
    }
    /**
     * 转换来源关联资料。
     */
    public OriginResponse origin(OriginEntity e, String sourceName) {
        return new OriginResponse(VideoValues.textId(e.getId()), VideoValues.textId(e.getSourceId()), sourceName,
                e.getUpstreamId(), VideoValues.time(e.getSourceUpdatedAt()), VideoValues.time(e.getCollectedAt()));
    }
    /**
     * 转换定时任务。
     */
    public TaskResponse task(TaskEntity e) {
        return new TaskResponse(VideoValues.textId(e.getId()), e.getName(), VideoValues.textId(e.getSourceId()),
                e.getRangeCode(), e.getUpstreamCategoryId(), e.getFrequency(), e.getIntervalHours(), e.getWeekday(),
                e.getTimeOfDay(), e.getZoneId(), Boolean.TRUE.equals(e.getEnabled()), VideoValues.time(e.getNextRunAt()));
    }
    /**
     * 转换采集运行，进度与统计不依赖当前线程。
     */
    public RunResponse run(RunEntity e) {
        return new RunResponse(VideoValues.textId(e.getId()), VideoValues.textId(e.getTaskId()),
                VideoValues.textId(e.getSourceId()), e.getRangeCode(), e.getUpstreamCategoryId(), e.getStatus(),
                e.getNextPage(), e.getPageCount(), e.getProcessed(), e.getInserted(), e.getUpdated(),
                e.getPending(), e.getSkipped(), e.getErrorMessage(), e.getWarning(),
                VideoValues.time(e.getCreatedAt()), VideoValues.time(e.getStartedAt()), VideoValues.time(e.getFinishedAt()));
    }
}
