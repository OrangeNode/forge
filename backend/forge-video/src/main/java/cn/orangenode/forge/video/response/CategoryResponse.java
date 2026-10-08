package cn.orangenode.forge.video.response;

import java.util.List;

/**
 * CategoryResponse对外业务响应，主键使用字符串；videoCount 为直属未删除影片数，只在管理端返回，公开接口为空。
 */
public record CategoryResponse(String id, String parentId, String name, int sortNo, boolean enabled, Long videoCount, List<CategoryResponse> children) {
}
