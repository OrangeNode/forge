package cn.orangenode.forge.example.controller.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.example.request.ValidationSampleRequest;
import cn.orangenode.forge.example.response.ValidationSampleResponse;
import cn.orangenode.forge.framework.validation.ForgeEnum;
import cn.orangenode.forge.framework.web.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 公共能力验证接口。
 *
 * <p>只用于验证统一响应、参数校验、字符串 ID 与文档声明，不访问数据库、不承载业务流程，
 * 与示例模块一起在 M6 移除。后续业务接口复用同样的注解与响应约定，而不是复制本类。</p>
 *
 * <p>每个方法只对应一个可独立验证的协议能力，避免形成“万能 Controller”。</p>
 */
@RestController
@RequestMapping("/api/admin/v1/example/validation")
public class ValidationSampleController {

    /**
     * 回显路径中的字符串 ID 与查询参数，用于验证方法参数校验与文档中的字符串 ID 声明。
     *
     * <p>ID 在路径中以字符串接收并声明为 {@link String}，长度校验负责拒绝明显越界的取值，
     * 因此不存在前端大整数精度问题；非法取值由全局异常处理器返回 400。</p>
     *
     * @param id           业务 ID 字符串
     * @param pageSize     每页条数，允许 {@code null} 表示未提交
     * @param status       状态枚举代码，只允许 active 或 disabled
     * @param request      当前 HTTP 请求，用于读取已分配的追踪编号
     * @return 含 ID、分页参数与状态代码的统一成功响应
     */
    @GetMapping("/{id}")
    public ApiResponse<ValidationSampleResponse> echo(
            @PathVariable @Size(max = 19, message = "ID 长度不能超过 19 位") String id,
            @RequestParam(required = false) @Min(value = 1, message = "每页条数不能小于 1") Integer pageSize,
            @RequestParam(required = false) @ForgeEnum(value = SampleStatus.class,
                    message = "状态只能是 active 或 disabled") String status,
            HttpServletRequest request) {
        ValidationSampleResponse data = new ValidationSampleResponse(id, pageSize, status);
        return ApiResponse.successWithTrace(data, TraceIdFilter.currentTraceId(request));
    }

    /**
     * 提交嵌套校验样例，用于验证嵌套对象与集合内的校验结果结构。
     *
     * @param requestPayload 嵌套校验请求体
     * @param request        当前 HTTP 请求，用于读取已分配的追踪编号
     * @return 校验通过后的统一成功响应
     */
    @PostMapping("/nested")
    public ApiResponse<ValidationSampleResponse> nested(@Valid @RequestBody ValidationSampleRequest requestPayload,
            HttpServletRequest request) {
        ValidationSampleResponse data = new ValidationSampleResponse(requestPayload.deptId(), requestPayload.pageNum(),
                requestPayload.status());
        return ApiResponse.successWithTrace(data, TraceIdFilter.currentTraceId(request));
    }

    /**
     * 状态枚举代码，用于验证接口不接受枚举序号与任意字符串。
     */
    public enum SampleStatus {

        /**
         * 启用状态。
         */
        active,

        /**
         * 停用状态。
         */
        disabled
    }
}
