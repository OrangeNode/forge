package cn.orangenode.forge.core.page;

import java.util.List;

/**
 * 分页查询结果。
 *
 * <p>对外只暴露记录列表与分页元数据，不暴露数据库分页对象或查询条件。
 * 本类型保持框架无关，不包含 Validation 注解；分页入参见 framework 的 Web 入参类型。</p>
 *
 * @param records  当前页记录，始终为非 {@code null} 的不可变列表
 * @param total    满足条件的记录总数
 * @param pageNum  当前页码
 * @param pageSize 每页条数
 * @param <T>      记录类型，通常为对外响应类型
 */
public record PageResponse<T>(List<T> records, long total, int pageNum, int pageSize) {

    /**
     * 规范化分页结果，保证记录列表非空且不可变，总数不为负。
     *
     * @param records  当前页记录，允许为 {@code null}
     * @param total    记录总数
     * @param pageNum  页码
     * @param pageSize 每页条数
     */
    public PageResponse {
        records = records == null ? List.of() : List.copyOf(records);
        total = Math.max(total, 0L);
    }

    /**
     * 构造空分页结果，用于查询条件无匹配记录的场景。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param <T>      记录类型
     * @return 记录为空的分页结果
     */
    public static <T> PageResponse<T> empty(int pageNum, int pageSize) {
        return new PageResponse<>(List.of(), 0L, pageNum, pageSize);
    }
}
