package cn.orangenode.forge.m2.pagination;

/**
 * 分页验证用的对外响应。
 *
 * <p>属于测试夹具：只暴露标题与优先级，不直接返回实体，符合“不把 Entity 暴露给前端”的约定。</p>
 *
 * @param id       业务 ID，对外为字符串
 * @param title    标题
 * @param priority 优先级
 */
public record PaginationProbeResponse(String id, String title, Integer priority) {
}
