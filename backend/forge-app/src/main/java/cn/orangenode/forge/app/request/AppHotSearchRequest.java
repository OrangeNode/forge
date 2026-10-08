package cn.orangenode.forge.app.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 热搜设置修改入参；空白词会被忽略，重复词只保留第一次出现。
 *
 * @param hotWords     手动热搜词，按顺序展示
 * @param blockedWords 屏蔽词，包含屏蔽词的热搜词不会返回给 App
 */
public record AppHotSearchRequest(
        @NotNull(message = "请填写热搜词列表")
        @Size(max = 50, message = "热搜词不能超过 50 个") List<@Size(max = 32, message = "单个热搜词不能超过 32 个字符") String> hotWords,
        @NotNull(message = "请填写屏蔽词列表")
        @Size(max = 200, message = "屏蔽词不能超过 200 个") List<@Size(max = 32, message = "单个屏蔽词不能超过 32 个字符") String> blockedWords) {
}
