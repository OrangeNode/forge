package cn.orangenode.forge.app.response;

/**
 * 推荐位中手动挑选的影片。
 *
 * @param id   影片 ID
 * @param name 影片名称，影片已删除时为空
 */
public record AppPickedVideoResponse(String id, String name) {
}
