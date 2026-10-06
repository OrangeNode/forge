package cn.orangenode.forge.video.collector;

/**
 * 按源顺序保存的分集及未经解码的完整地址。
 */
public record CollectedEpisode(String name, String url, String urlType) {
}
