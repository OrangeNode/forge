package cn.orangenode.forge.video.service;

import cn.orangenode.forge.video.collector.CollectedPage;
import cn.orangenode.forge.video.request.PendingResolveRequest;

/**
 * 采集入库及人工确认的事务边界。
 */
public interface IngestService {
    /**
     * 在短事务中入库整页并提交断点，网络请求由调用方完成。
     */
    void page(Long runId, CollectedPage page);
    /**
     * 人工确认存疑数据，来源关联与线路一起提交。
     */
    void resolve(Long pendingId, PendingResolveRequest request);

    /**
     * 以最新采集规则重新判断单条待确认数据，仍存疑时拒绝自动入库。
     */
    void retryPending(Long pendingId);
}
