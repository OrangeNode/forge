package cn.orangenode.forge.app.response;

/**
 * 播放页滚动文字。
 *
 * @param enabled 是否启用
 * @param text    滚动文字
 * @param color   文字颜色 {@code #RRGGBB}，未设置时为空
 */
public record AppMarqueeResponse(boolean enabled, String text, String color) {
}
