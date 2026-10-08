package cn.orangenode.forge.member.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 上报观看进度入参。
 *
 * @param lineId          播放线路 ID，可为空
 * @param episodeIndex    分集序号，从 0 开始
 * @param episodeName     分集名称
 * @param positionSeconds 播放位置秒数
 * @param durationSeconds 分集总时长秒数，未知为 0
 */
public record MemberHistoryRequest(
        String lineId,

        @NotNull(message = "缺少分集序号")
        @Min(value = 0, message = "分集序号不能小于 0")
        @Max(value = 100000, message = "分集序号超出范围")
        Integer episodeIndex,

        @NotBlank(message = "缺少分集名称")
        @Size(max = 64, message = "分集名称不能超过 64 个字符")
        String episodeName,

        @NotNull(message = "缺少播放位置")
        @Min(value = 0, message = "播放位置不能小于 0")
        @Max(value = 864000, message = "播放位置超出范围")
        Integer positionSeconds,

        @NotNull(message = "缺少分集时长")
        @Min(value = 0, message = "分集时长不能小于 0")
        @Max(value = 864000, message = "分集时长超出范围")
        Integer durationSeconds) {
}
