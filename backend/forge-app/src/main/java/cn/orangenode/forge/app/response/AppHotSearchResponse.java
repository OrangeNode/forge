package cn.orangenode.forge.app.response;

import java.util.List;

/**
 * 热搜设置。
 *
 * @param hotWords     手动热搜词
 * @param blockedWords 屏蔽词
 */
public record AppHotSearchResponse(List<String> hotWords, List<String> blockedWords) {
}
