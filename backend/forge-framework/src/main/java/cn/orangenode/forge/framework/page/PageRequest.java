package cn.orangenode.forge.framework.page;

import org.springframework.beans.factory.annotation.Value;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 分页查询入参。
 *
 * <p>属于 Web 入参类型，因此依赖 Jakarta Validation 注解并放在 framework；
 * 框架无关的分页结果 {@code cn.orangenode.forge.core.page.PageResponse} 位于 core。</p>
 *
 * <p>默认页码、默认条数与条数上限全部来自配置 {@code forge.page}：前两者通过 {@code @Value} 注入，
 * 上限通过 {@link #validatePageSize()} 按配置校验。之所以不把上限写进 {@code @Max} 注解，
 * 是因为注解的属性值只能是编译期常量或表达式字符串，把上限写进注解会让配置不再是唯一来源。</p>
 */
public class PageRequest {

    /**
     * 每页条数的硬上限，超出该值的请求参数在类型层面即视为非法。
     */
    private static final int ABSOLUTE_MAX_PAGE_SIZE = 1000;

    /**
     * 未传参数时使用的页码，来自 {@code forge.page.default-page-num}。
     */
    @Value("${forge.page.default-page-num:1}")
    private int defaultPageNum = 1;

    /**
     * 未传参数时使用的每页条数，来自 {@code forge.page.default-page-size}。
     */
    @Value("${forge.page.default-page-size:20}")
    private int defaultPageSize = 20;

    /**
     * 每页条数上限，来自 {@code forge.page.max-size}。
     */
    @Value("${forge.page.max-size:100}")
    private int configuredMaxPageSize = 100;

    /**
     * 页码，从 1 开始。
     */
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum;

    /**
     * 每页条数，不能小于 1，且不能超过硬上限。
     */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = ABSOLUTE_MAX_PAGE_SIZE, message = "每页条数超出允许的硬上限")
    private Integer pageSize;

    /**
     * 按配置校验每页条数是否在允许范围内。
     *
     * <p>由请求层在进入业务逻辑前调用；未传每页条数时使用默认值，视为合法。</p>
     *
     * @return 合法时返回 {@code true}
     */
    public boolean validatePageSize() {
        int effective = getPageSize();
        return effective >= 1 && effective <= configuredMaxPageSize;
    }

    /**
     * 取得配置的每页条数上限。
     *
     * @return 每页条数上限
     */
    public int getConfiguredMaxPageSize() {
        return configuredMaxPageSize;
    }

    /**
     * 设置每页条数上限，只用于配置注入与测试。
     *
     * @param configuredMaxPageSize 每页条数上限
     */
    public void setConfiguredMaxPageSize(int configuredMaxPageSize) {
        this.configuredMaxPageSize = configuredMaxPageSize;
    }

    /**
     * 取得页码，未设置时返回配置的默认页码。
     *
     * @return 大于等于 1 的页码
     */
    public int getPageNum() {
        return pageNum == null ? defaultPageNum : pageNum;
    }

    /**
     * 设置页码。
     *
     * @param pageNum 页码，从 1 开始
     */
    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    /**
     * 取得每页条数，未设置时返回配置的默认条数。
     *
     * @return 每页条数
     */
    public int getPageSize() {
        return pageSize == null ? defaultPageSize : pageSize;
    }

    /**
     * 设置每页条数。
     *
     * @param pageSize 每页条数，不能超过配置的上限
     */
    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * 设置未传参数时使用的页码，只用于配置注入与测试。
     *
     * @param defaultPageNum 默认页码
     */
    public void setDefaultPageNum(int defaultPageNum) {
        this.defaultPageNum = defaultPageNum;
    }

    /**
     * 设置未传参数时使用的每页条数，只用于配置注入与测试。
     *
     * @param defaultPageSize 默认每页条数
     */
    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    /**
     * 计算当前页第一条记录的下标。
     *
     * <p>使用 {@code long} 计算，避免页码较大时乘法溢出。</p>
     *
     * @return 从 0 开始的偏移量
     */
    public long getOffset() {
        return (long) (getPageNum() - 1) * getPageSize();
    }
}
