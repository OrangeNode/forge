package cn.orangenode.forge.video.collector;

/**
 * 服务端代取的远程图片，只在内存中短暂持有，不落盘。
 *
 * @param bytes       图片字节
 * @param contentType 远程响应声明的图片类型
 */
public record RemoteImage(byte[] bytes, String contentType) {
}
