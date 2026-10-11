package cn.orangenode.forge.app.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 播放页滚动文字修改入参。
 *
 * @param enabled 是否启用；启用时文字不能为空
 * @param text    滚动文字
 * @param color   文字颜色 {@code #RRGGBB}
 */
public record AppMarqueeRequest(
        @NotNull(message = "请选择是否启用") Boolean enabled,
        @Size(max = 200, message = "滚动文字不能超过 200 个字符") String text,
        @NotNull(message = "请选择文字颜色")
        @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "颜色必须是 #RRGGBB 格式") String color) {
}
