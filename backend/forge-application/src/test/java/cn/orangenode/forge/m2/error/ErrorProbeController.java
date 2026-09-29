package cn.orangenode.forge.m2.error;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.dynamic.datasource.annotation.DS;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.exception.DependencyUnavailableException;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.redis.ForgeRedisTemplate;
import cn.orangenode.forge.framework.web.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 统一错误出口验证用的探针接口。
 *
 * <p>属于测试夹具：每个方法只触发一种失败路径，用于断言 HTTP 状态与 {@code body.code}
 * 的一致性，不进入生产模块，也不承载业务流程。</p>
 *
 * <p>探针覆盖业务异常、未预期异常、依赖不可用、方法参数校验、请求体校验与未注册数据源。</p>
 */
@RestController
@RequestMapping("/api/admin/v1/m2/probe")
public class ErrorProbeController {

    /**
     * 项目级 Redis 入口，用于验证依赖故障的失败语义。
     */
    private final ForgeRedisTemplate forgeRedisTemplate;

    /**
     * 路由数据源上的 JDBC 模板，用于验证未注册数据源名称的失败路径。
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造错误路径探针接口。
     *
     * @param forgeRedisTemplate 项目级 Redis 入口
     * @param jdbcTemplate       路由数据源上的 JDBC 模板
     */
    public ErrorProbeController(ForgeRedisTemplate forgeRedisTemplate, JdbcTemplate jdbcTemplate) {
        this.forgeRedisTemplate = forgeRedisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 返回成功响应，用于对照成功路径的 HTTP 状态与追踪编号。
     *
     * @param request 当前 HTTP 请求
     * @return 统一成功响应
     */
    @GetMapping("/success")
    public ApiResponse<String> success(HttpServletRequest request) {
        return ApiResponse.successWithTrace("ok", TraceIdFilter.currentTraceId(request));
    }

    /**
     * 抛出携带结果码的业务异常。
     *
     * @return 不会返回，仅用于类型声明
     */
    @GetMapping("/business-conflict")
    public ApiResponse<String> businessConflict() {
        throw BusinessException.of(ErrorCode.CONFLICT, "业务状态冲突");
    }

    /**
     * 抛出未预期异常，用于验证对外只给通用说明。
     *
     * @return 不会返回，仅用于类型声明
     */
    @GetMapping("/unexpected")
    public ApiResponse<String> unexpected() {
        throw new IllegalStateException("内部实现细节不应出现在响应中");
    }

    /**
     * 抛出依赖不可用异常，用于验证 503 语义。
     *
     * @return 不会返回，仅用于类型声明
     */
    @GetMapping("/dependency-down")
    public ApiResponse<String> dependencyDown() {
        throw new DependencyUnavailableException("依赖暂不可用");
    }

    /**
     * 访问 Redis，用于验证 Redis 故障时不继续执行业务且返回 503。
     *
     * @return 不会返回，仅用于类型声明
     */
    @GetMapping("/redis-down")
    public ApiResponse<String> redisDown() {
        forgeRedisTemplate.get("auth:probe");
        return ApiResponse.ok("不应到达");
    }

    /**
     * 在未注册的数据源上执行查询，用于验证数据源名称严格匹配且不回退主库。
     *
     * @return 不会返回，仅用于类型声明
     */
    @GetMapping("/unknown-datasource")
    @DS("not-exists")
    public ApiResponse<String> unknownDataSource() {
        jdbcTemplate.queryForList("select 1");
        return ApiResponse.ok("不应到达");
    }

    /**
     * 接收带约束的查询参数，用于验证方法参数校验的字段错误结构。
     *
     * @param pageSize 每页条数，1 到 100
     * @return 校验通过时的统一成功响应
     */
    @GetMapping("/method-parameter")
    public ApiResponse<Integer> methodParameter(
            @RequestParam @Min(value = 1, message = "每页条数不能小于 1")
            @Max(value = 100, message = "每页条数不能超过 100") int pageSize) {
        return ApiResponse.ok(pageSize);
    }

    /**
     * 接收带嵌套校验的请求体，用于验证 {@code data.fieldErrors} 结构。
     *
     * @param request 请求体
     * @return 校验通过时的统一成功响应
     */
    @PostMapping("/body")
    public ApiResponse<String> body(@Valid @RequestBody ErrorProbeRequest request) {
        return ApiResponse.ok(request.name());
    }

    /**
     * 只接受 JSON 请求体，用于验证媒体类型不受支持时的错误出口。
     *
     * @param body 请求体原文
     * @return 不会返回，仅用于类型声明
     */
    @PostMapping(value = "/text-body", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<String> textBody(@RequestBody String body) {
        return ApiResponse.ok(body);
    }

    /**
     * 嵌套校验请求体。
     *
     * @param name   名称，必填且长度不超过 8
     * @param nested 嵌套对象，必须整体通过校验
     */
    public record ErrorProbeRequest(
            @NotBlank(message = "名称不能为空")
            @Size(max = 8, message = "名称长度不能超过 8")
            String name,

            @NotNull(message = "嵌套对象不能为空")
            @Valid
            ErrorProbeNested nested) {
    }

    /**
     * 嵌套校验对象。
     *
     * @param code 编码，必填且长度不超过 4
     */
    public record ErrorProbeNested(
            @NotBlank(message = "嵌套编码不能为空")
            @Size(max = 4, message = "嵌套编码长度不能超过 4")
            String code) {
    }
}
