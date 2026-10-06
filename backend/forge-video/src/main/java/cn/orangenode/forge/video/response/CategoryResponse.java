package cn.orangenode.forge.video.response;

import java.util.List;

/**
 * CategoryResponse对外业务响应，主键使用字符串。
 */
public record CategoryResponse(String id, String parentId, String name, int sortNo, boolean enabled, List<CategoryResponse> children) {
}
