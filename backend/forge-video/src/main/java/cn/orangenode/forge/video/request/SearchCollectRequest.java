package cn.orangenode.forge.video.request;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * 勾选的搜索结果，按资源站分组后各提交一条采集运行。
 */
public record SearchCollectRequest(@NotEmpty List<@Valid SearchCollectItem> items) {
}
