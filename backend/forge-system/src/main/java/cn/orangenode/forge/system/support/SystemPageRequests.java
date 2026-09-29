package cn.orangenode.forge.system.support;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.config.ForgePageProperties;
import cn.orangenode.forge.framework.page.PageRequest;

/**
 * 系统管理分页入口校验。
 *
 * <p>每页条数上限来自配置 {@code forge.page.max-size}，默认页码与默认条数同样来自配置；
 * 各查询接口为每次请求构造一个独立的分页入参，越界时统一抛出一个带配置上限的中文提示，
 * 避免在每个 Controller 各写一遍写死上限的注解。</p>
 *
 * <p>不把 {@link PageRequest} 装配成单例 Bean：它承载单次请求的页码与条数，
 * 单例会在线程之间互相覆盖。这里按请求创建实例，配置值由调用方注入的
 * {@link ForgePageProperties} 提供。</p>
 */
public final class SystemPageRequests {

    /**
     * 工具类不允许实例化。
     */
    private SystemPageRequests() {
    }

    /**
     * 按请求参数与配置构造分页入参并校验。
     *
     * @param pageNum    请求中的页码，允许为空表示使用配置默认值
     * @param pageSize   请求中的每页条数，允许为空表示使用配置默认值
     * @param properties 分页配置，提供默认值与上限
     * @return 通过校验的分页入参
     * @throws BusinessException 页码小于 1 或每页条数超出配置范围时抛出 400
     */
    public static PageRequest resolve(Integer pageNum, Integer pageSize, ForgePageProperties properties) {
        PageRequest request = new PageRequest();
        request.setDefaultPageNum(properties.getDefaultPageNum());
        request.setDefaultPageSize(properties.getDefaultPageSize());
        request.setConfiguredMaxPageSize(properties.getMaxPageSize());
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);
        requireValid(request);
        return request;
    }

    /**
     * 校验分页参数是否在允许范围内。
     *
     * @param request 分页入参
     * @throws BusinessException 页码小于 1 或每页条数超过配置上限时抛出 400
     */
    public static void requireValid(PageRequest request) {
        if (request.getPageNum() < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "页码不能小于 1");
        }
        if (!request.validatePageSize()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "每页条数不能超过 " + request.getConfiguredMaxPageSize());
        }
    }
}
