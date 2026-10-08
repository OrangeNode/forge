package cn.orangenode.forge.app.service;

import cn.orangenode.forge.app.request.AppFeedbackHandleRequest;
import cn.orangenode.forge.app.request.AppFeedbackSubmitRequest;
import cn.orangenode.forge.app.response.AppFeedbackResponse;
import cn.orangenode.forge.core.page.PageResponse;

/**
 * 意见反馈与播放报错：App 提交，后台查看与处理。
 */
public interface AppFeedbackService {

    /**
     * 当前普通用户提交反馈，超过每日条数 429。
     *
     * @param request 反馈内容
     */
    void submit(AppFeedbackSubmitRequest request);

    /**
     * 后台分页查询，待处理在前。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param status   状态筛选，可为空
     * @param type     类型筛选，可为空
     * @return 反馈分页
     */
    PageResponse<AppFeedbackResponse> page(Integer pageNum, Integer pageSize, String status, String type);

    /**
     * 后台标记已处理，不存在 404，已处理 409。
     *
     * @param id      反馈 ID
     * @param request 处理备注
     */
    void resolve(Long id, AppFeedbackHandleRequest request);
}
