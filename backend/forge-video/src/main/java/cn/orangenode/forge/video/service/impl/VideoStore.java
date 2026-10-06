package cn.orangenode.forge.video.service.impl;

import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.video.entity.CategoryEntity;
import cn.orangenode.forge.video.mapper.CategoryMapper;
import cn.orangenode.forge.video.entity.SourceEntity;
import cn.orangenode.forge.video.mapper.SourceMapper;
import cn.orangenode.forge.video.entity.SourceCategoryEntity;
import cn.orangenode.forge.video.mapper.SourceCategoryMapper;
import cn.orangenode.forge.video.entity.VideoEntity;
import cn.orangenode.forge.video.mapper.VideoMapper;
import cn.orangenode.forge.video.entity.OriginEntity;
import cn.orangenode.forge.video.mapper.OriginMapper;
import cn.orangenode.forge.video.entity.PlayLineEntity;
import cn.orangenode.forge.video.mapper.PlayLineMapper;
import cn.orangenode.forge.video.entity.EpisodeEntity;
import cn.orangenode.forge.video.mapper.EpisodeMapper;
import cn.orangenode.forge.video.entity.SettingsEntity;
import cn.orangenode.forge.video.mapper.SettingsMapper;
import cn.orangenode.forge.video.entity.TaskEntity;
import cn.orangenode.forge.video.mapper.TaskMapper;
import cn.orangenode.forge.video.entity.RunEntity;
import cn.orangenode.forge.video.mapper.RunMapper;
import cn.orangenode.forge.video.entity.PendingEntity;
import cn.orangenode.forge.video.mapper.PendingMapper;
import lombok.RequiredArgsConstructor;

/**
 * 视频模块内部的持久化访问与存在性校验，不对控制器公开。
 */
@Component
@RequiredArgsConstructor
public class VideoStore {
    final CategoryMapper categories;
    final SourceMapper sources;
    final SourceCategoryMapper sourceCategories;
    final VideoMapper videos;
    final OriginMapper origins;
    final PlayLineMapper playLines;
    final EpisodeMapper episodes;
    final SettingsMapper settings;
    final TaskMapper tasks;
    final RunMapper runs;
    final PendingMapper pendings;

    /**
     * 读取有效的Category记录，不存在时返回业务错误。
     */
    public CategoryEntity requireCategory(Long id) {
        CategoryEntity entity = categories.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "视频分类不存在");
        }
        return entity;
    }

    /**
     * 读取有效的Source记录，不存在时返回业务错误。
     */
    public SourceEntity requireSource(Long id) {
        SourceEntity entity = sources.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "资源站不存在");
        }
        return entity;
    }

    /**
     * 读取有效的Video记录，不存在时返回业务错误。
     */
    public VideoEntity requireVideo(Long id) {
        VideoEntity entity = videos.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "影片不存在");
        }
        return entity;
    }

    /**
     * 在写事务中锁定影片，使人工资料锁定和采集更新按数据库提交顺序生效。
     */
    public VideoEntity lockVideo(Long id) {
        VideoEntity entity = videos.selectOne(Wrappers.<VideoEntity>lambdaQuery()
                .eq(VideoEntity::getId, id).last("FOR UPDATE"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "影片不存在");
        }
        return entity;
    }

    /**
     * 读取有效的Origin记录，不存在时返回业务错误。
     */
    public OriginEntity requireOrigin(Long id) {
        OriginEntity entity = origins.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "来源记录不存在");
        }
        return entity;
    }

    /**
     * 读取有效的Run记录，不存在时返回业务错误。
     */
    public RunEntity requireRun(Long id) {
        RunEntity entity = runs.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "采集运行不存在");
        }
        return entity;
    }

    /**
     * 读取有效的Task记录，不存在时返回业务错误。
     */
    public TaskEntity requireTask(Long id) {
        TaskEntity entity = tasks.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "采集任务不存在");
        }
        return entity;
    }

    /**
     * 读取有效的PlayLine记录，不存在时返回业务错误。
     */
    public PlayLineEntity requirePlayLine(Long id) {
        PlayLineEntity entity = playLines.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "播放线路不存在");
        }
        return entity;
    }

    /**
     * 读取有效的Pending记录，不存在时返回业务错误。
     */
    public PendingEntity requirePending(Long id) {
        PendingEntity entity = pendings.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "待确认数据不存在");
        }
        return entity;
    }

    /**
     * 校验分类及其所有祖先处于可用状态。
     */
    public boolean categoryVisible(Long id) {
        java.util.Set<Long> visited = new java.util.HashSet<>();
        while (id != null && id != 0L) {
            CategoryEntity category = categories.selectById(id);
            if (category == null || !Boolean.TRUE.equals(category.getEnabled()) || !visited.add(id)) {
                return false;
            }
            id = category.getParentId();
        }
        return true;
    }

    /**
     * 查找同一来源的稳定原始标识。
     */
    public OriginEntity findOrigin(Long sourceId, String upstreamId) {
        return origins.selectOne(Wrappers.<OriginEntity>lambdaQuery()
                .eq(OriginEntity::getSourceId, sourceId).eq(OriginEntity::getUpstreamId, upstreamId));
    }
}
