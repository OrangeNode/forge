package cn.orangenode.forge.video.response;

import java.util.List;

/**
 * VideoDetailResponse对外业务响应，主键使用字符串。
 */
public record VideoDetailResponse(VideoResponse video, List<OriginResponse> origins, List<PlayLineResponse> playLines) {
}
