package cn.orangenode.forge.video.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 一条勾选的搜索结果：资源站主键与源站影片编号（不含逗号）。
 */
public record SearchCollectItem(@NotBlank String sourceId, @NotBlank @Size(max = 128) @Pattern(regexp = "[^,]+") String upstreamId) {
}
