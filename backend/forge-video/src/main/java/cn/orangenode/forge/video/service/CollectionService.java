package cn.orangenode.forge.video.service;

import java.util.List;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.video.request.TaskRequest;
import cn.orangenode.forge.video.request.CollectRequest;
import cn.orangenode.forge.video.response.TaskResponse;
import cn.orangenode.forge.video.response.RunResponse;
import cn.orangenode.forge.video.response.PendingResponse;

/**
 * 持久化采集队列与调度用例。
 */
public interface CollectionService {
    /**
     * 查询定时任务。
     */
    List<TaskResponse> tasks();

    /**
     * 保存定时任务，新任务默认停用。
     */
    TaskResponse saveTask(Long id, TaskRequest request);

    /**
     * 删除没有活动运行的定时任务。
     */
    void deleteTask(Long id);

    /**
     * 提交手动采集，立即返回运行编号。
     */
    RunResponse collect(CollectRequest request);

    /**
     * 提交勾选采集：按源站影片编号请求详情，入库规则与范围采集一致，立即返回运行编号。
     */
    RunResponse collectSelected(Long sourceId, List<String> upstreamIds);

    /**
     * 预览完整源站采集URL。
     */
    String preview(CollectRequest request);

    /**
     * 查询运行分页。
     */
    PageResponse<RunResponse> runs(Integer pageNum, Integer pageSize, String status);

    /**
     * 查询持久化进度。
     */
    RunResponse run(Long id);

    /**
     * 请求停止或取消排队运行。
     */
    void stop(Long id);

    /**
     * 从上次已提交页面继续。
     */
    void resume(Long id);

    /**
     * 查询待确认与已处理记录。
     */
    PageResponse<PendingResponse> pending(Integer pageNum, Integer pageSize, String status, String runId);

    /**
     * 补触发到期任务并推进下一触发时间。
     */
    void dispatchDue();

    /**
     * 顺序取得一个排队运行。
     */
    Long claim();

    /**
     * 结束运行并保留断点。
     */
    void finish(Long id, String status, String error);

    /**
     * 重启后将未完成运行标记中断。
     */
    void recover();

    /**
     * 清理超过保留期的终态运行。
     */
    void cleanup();

}
