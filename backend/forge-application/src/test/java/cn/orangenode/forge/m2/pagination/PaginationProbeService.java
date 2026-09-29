package cn.orangenode.forge.m2.pagination;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.framework.page.PageRequest;
import cn.orangenode.forge.framework.page.PageResponses;

/**
 * 分页验证用例入口。
 *
 * <p>属于测试夹具：演示业务模块使用分页的标准写法——用 {@link PageRequest} 的页码与条数构造
 * MyBatis-Plus 的分页对象，交给映射器查询，再用 {@link PageResponses} 转成对外结构。
 * 不引入额外封装类型，排序由具体业务查询的 SQL 自行决定。</p>
 */
@Component
public class PaginationProbeService {

    /**
     * 探针持久化接口。
     */
    private final PaginationProbeMapper probeMapper;

    /**
     * 构造分页验证用例入口。
     *
     * @param probeMapper 探针持久化接口
     */
    public PaginationProbeService(PaginationProbeMapper probeMapper) {
        this.probeMapper = probeMapper;
    }

    /**
     * 执行分页查询并转换为对外结构。
     *
     * @param request 分页入参
     * @return 对外分页结果
     */
    public PageResponse<PaginationProbeResponse> page(PageRequest request) {
        Page<PaginationProbeEntity> page = new Page<>(request.getPageNum(), request.getPageSize());
        return PageResponses.from(probeMapper.selectPage(page, null),
                entity -> new PaginationProbeResponse(String.valueOf(entity.getId()), entity.getTitle(),
                        entity.getPriority()));
    }
}
