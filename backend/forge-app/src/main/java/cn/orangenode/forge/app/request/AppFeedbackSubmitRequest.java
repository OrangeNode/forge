package cn.orangenode.forge.app.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * App 提交意见反馈或播放报错。
 *
 * @param type        PLAYBACK 播放报错（影片必填）/ GENERAL 意见反馈
 * @param videoId     影片 ID
 * @param lineId      播放线路 ID
 * @param lineName    线路名称
 * @param episodeName 分集名称
 * @param content     反馈内容
 * @param deviceName  设备名称
 * @param appVersion  App 版本
 */
public record AppFeedbackSubmitRequest(
        @NotBlank(message = "缺少反馈类型")
        @Pattern(regexp = "PLAYBACK|GENERAL", message = "反馈类型不正确")
        String type,

        String videoId,

        String lineId,

        @Size(max = 128, message = "线路名称不能超过 128 个字符")
        String lineName,

        @Size(max = 64, message = "分集名称不能超过 64 个字符")
        String episodeName,

        @NotBlank(message = "请填写反馈内容")
        @Size(max = 500, message = "反馈内容不能超过 500 个字符")
        String content,

        @Size(max = 64, message = "设备名称不能超过 64 个字符")
        String deviceName,

        @Size(max = 32, message = "版本号不能超过 32 个字符")
        String appVersion) {
}
