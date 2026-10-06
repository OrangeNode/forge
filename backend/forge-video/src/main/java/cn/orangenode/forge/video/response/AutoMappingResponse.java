package cn.orangenode.forge.video.response;

import java.util.List;

/**
 * 自动分类操作统计及仍需人工确认的分类编号。
 */
public record AutoMappingResponse(int created, int bound, int preserved, List<String> unresolved) {
}
