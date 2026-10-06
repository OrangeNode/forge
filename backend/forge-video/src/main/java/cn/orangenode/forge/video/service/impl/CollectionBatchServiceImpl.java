package cn.orangenode.forge.video.service.impl;

import java.util.List;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.video.entity.RunEntity;
import cn.orangenode.forge.video.entity.PendingEntity;
import cn.orangenode.forge.video.request.CollectRequest;
import cn.orangenode.forge.video.request.CollectionBatchRequest;
import cn.orangenode.forge.video.response.CollectionBatchResponse;
import cn.orangenode.forge.video.service.CollectionBatchService;
import cn.orangenode.forge.video.service.CollectionService;
import cn.orangenode.forge.video.service.IngestService;
import cn.orangenode.forge.video.support.VideoValues;
import lombok.RequiredArgsConstructor;

/**
 * 先固定目标编号，再按条提交；失败不掩盖成功，全部范围由服务端筛选。
 */
@Service
@RequiredArgsConstructor
public class CollectionBatchServiceImpl implements CollectionBatchService {
    private final VideoStore store;
    private final CollectionService collections;
    private final IngestService ingest;
    private final TransactionTemplate transactions;

    /**
     * 全部包含所有匹配状态的历史记录，不局限于当前页。
     */
    @Override
    public CollectionBatchResponse runs(CollectionBatchRequest request) {
        List<Long> ids = "ALL".equals(request.scope())
                ? store.runs.selectList(Wrappers.<RunEntity>lambdaQuery()
                        .eq(VideoValues.clean(request.status()) != null, RunEntity::getStatus, request.status())
                        .orderByAsc(RunEntity::getId)).stream().map(RunEntity::getId).toList() : selected(request);
        return execute(ids, id -> {
            RunEntity run = store.requireRun(id);
            switch (request.action()) {
                case "COLLECT" -> collections.collect(new CollectRequest(run.getSourceId().toString(),
                        run.getRangeCode(), run.getUpstreamCategoryId()));
                case "STOP" -> collections.stop(id);
                case "DELETE" -> {
                    RunEntity locked = store.runs.selectOne(Wrappers.<RunEntity>lambdaQuery()
                            .eq(RunEntity::getId, id).last("FOR UPDATE"));
                    if (locked == null || List.of("QUEUED", "RUNNING", "STOP_REQUESTED").contains(locked.getStatus())) {
                        throw new BusinessException(ErrorCode.CONFLICT, "活动运行不能删除，请先停止");
                    }
                    store.pendings.update(Wrappers.<PendingEntity>lambdaUpdate().eq(PendingEntity::getRunId, id)
                            .set(PendingEntity::getRunId, null));
                    store.runs.deleteById(id);
                }
                default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "批量操作不合法");
            }
        });
    }

    /**
     * 待确认采集按最新设置重新判断，删除保留忽略身份以免重采恢复。
     */
    @Override
    public CollectionBatchResponse pending(CollectionBatchRequest request) {
        if (!List.of("COLLECT", "DELETE").contains(request.action())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "待确认数据只支持采集或删除");
        }
        List<Long> ids = "ALL".equals(request.scope())
                ? store.pendings.selectList(Wrappers.<PendingEntity>lambdaQuery()
                        .ne(PendingEntity::getStatus, "DELETED")
                        .eq(VideoValues.clean(request.status()) != null, PendingEntity::getStatus, request.status())
                        .eq(VideoValues.clean(request.runId()) != null, PendingEntity::getRunId,
                                VideoValues.clean(request.runId()) == null ? null : VideoValues.id(request.runId()))
                        .orderByAsc(PendingEntity::getId)).stream().map(PendingEntity::getId).toList() : selected(request);
        return execute(ids, id -> {
            if ("COLLECT".equals(request.action())) {
                ingest.retryPending(id);
            } else {
                store.sources.lock(store.requirePending(id).getSourceId());
                store.pendings.update(Wrappers.<PendingEntity>lambdaUpdate().eq(PendingEntity::getId, id)
                        .set(PendingEntity::getStatus, "DELETED").set(PendingEntity::getUpdatedAt, VideoValues.now()));
            }
        });
    }

    /**
     * 勾选范围必须提供编号，去重后校验字符串主键。
     */
    private List<Long> selected(CollectionBatchRequest request) {
        if (!"SELECTED".equals(request.scope()) || request.ids() == null || request.ids().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先勾选要操作的记录");
        }
        return request.ids().stream().map(VideoValues::id).distinct().toList();
    }

    /**
     * 每条记录使用独立事务，业务失败逐条返回；系统故障保留统一错误出口。
     */
    private CollectionBatchResponse execute(List<Long> ids, java.util.function.Consumer<Long> action) {
        List<CollectionBatchResponse.Failure> failures = new ArrayList<>();
        int succeeded = 0;
        for (Long id : ids) {
            try {
                transactions.executeWithoutResult(status -> action.accept(id));
                succeeded++;
            } catch (BusinessException failure) {
                failures.add(new CollectionBatchResponse.Failure(id.toString(), failure.getMessage()));
            }
        }
        return new CollectionBatchResponse(ids.size(), succeeded, failures);
    }
}
