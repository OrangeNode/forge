package cn.orangenode.forge.app.request;

import jakarta.validation.constraints.Size;

/**
 * 后台标记反馈已处理。
 *
 * @param remark 处理备注，可为空
 */
public record AppFeedbackHandleRequest(
        @Size(max = 255, message = "处理备注不能超过 255 个字符")
        String remark) {
}
