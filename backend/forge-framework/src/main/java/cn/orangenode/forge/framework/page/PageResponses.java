package cn.orangenode.forge.framework.page;

import java.util.List;
import java.util.function.Function;

import com.baomidou.mybatisplus.core.metadata.IPage;

import cn.orangenode.forge.core.page.PageResponse;

/**
 * MyBatis-Plus 分页结果到对外分页结构的转换。
 *
 * <p>对外的 {@link PageResponse} 只包含记录列表与分页元数据；
 * {@code IPage}、查询包装器与实体都不会出现在接口层。</p>
 *
 * <p>空页与超出最后一页都返回空数组与真实总数，不返回 {@code null}。</p>
 */
public final class PageResponses {

    /**
     * 工具类不允许实例化。
     */
    private PageResponses() {
    }

    /**
     * 把分页查询结果映射为对外分页结构。
     *
     * @param source MyBatis-Plus 分页结果
     * @param mapper 实体到对外响应类型的显式转换函数
     * @param <E>    实体类型
     * @param <R>    对外响应类型
     * @return 对外分页结果
     */
    public static <E, R> PageResponse<R> from(IPage<E> source, Function<E, R> mapper) {
        List<R> records = source.getRecords().stream().map(mapper).toList();
        return new PageResponse<>(records, source.getTotal(), (int) source.getCurrent(), (int) source.getSize());
    }
}
