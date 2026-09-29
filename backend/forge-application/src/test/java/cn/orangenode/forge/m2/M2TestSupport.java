package cn.orangenode.forge.m2;

/**
 * M2 端到端验证共用的测试约定。
 *
 * <p>测试客户端使用 Spring Framework 7 的 {@code RestTestClient}：它支持真实 HTTP 往返，
 * 并且能同时断言 HTTP 状态、响应头与原始响应体，适合验证统一响应协议与框架默认错误出口。</p>
 *
 * <p>客户端必须在测试方法执行阶段按随机端口创建，因此不使用 {@code @TestConfiguration} 里的 Bean 工厂方法；
 * 各用例通过 {@code @LocalServerPort} 注入端口后自行构建。本类只作为说明与常量约定的落点。</p>
 */
public final class M2TestSupport {

    /**
     * 统一错误出口探针接口前缀。
     */
    public static final String ERROR_PROBE = "/api/admin/v1/m2/probe";

    /**
     * 工具类不允许实例化。
     */
    private M2TestSupport() {
    }
}
