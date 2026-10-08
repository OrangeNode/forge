package cn.orangenode.forge.app.response;

import java.util.List;

/**
 * App 热搜词。
 *
 * @param words 已排除屏蔽词的热搜词，按顺序展示
 */
public record AppHotWordsResponse(List<String> words) {
}
