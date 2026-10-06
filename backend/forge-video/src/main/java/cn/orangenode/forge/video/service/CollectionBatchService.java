package cn.orangenode.forge.video.service;

import cn.orangenode.forge.video.request.CollectionBatchRequest;
import cn.orangenode.forge.video.response.CollectionBatchResponse;

/**
 * 采集记录批量操作契约，逐条提交并汇总结果。
 */
public interface CollectionBatchService {
    /**
     * 对运行历史批量重新采集、停止或删除。
     */
    CollectionBatchResponse runs(CollectionBatchRequest request);

    /**
     * 对待确认数据按最新规则入库或删除。
     */
    CollectionBatchResponse pending(CollectionBatchRequest request);
}
