package cn.orangenode.forge.example.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 嵌套校验样例请求。
 *
 * <p>用于验证嵌套对象与集合内元素同时生效：外层字段与内层字段的校验失败都要出现在
 * {@code data.fieldErrors} 中，字段名带嵌套路径。</p>
 *
 * @param deptId          部门 ID 字符串，必填
 * @param pageNum         页码，1 到 100
 * @param status          状态枚举代码，长度不超过 8
 * @param tag            标签，长度 1 到 8
 * @param nested          嵌套对象，必须整体通过校验
 */
public record ValidationSampleRequest(
        @NotBlank(message = "部门 ID 不能为空")
        @Size(max = 19, message = "部门 ID 长度不能超过 19 位")
        String deptId,

        @NotNull(message = "页码不能为空")
        @Min(value = 1, message = "页码不能小于 1")
        @Max(value = 100, message = "页码不能超过 100")
        Integer pageNum,

        @Size(max = 8, message = "状态代码长度不能超过 8")
        String status,

        @Size(min = 1, max = 8, message = "标签长度必须在 1 到 8 之间")
        String tag,

        @NotNull(message = "嵌套对象不能为空")
        @Valid
        NestedSample nested) {

    /**
     * 嵌套对象样例，用于验证嵌套校验失败时的字段路径。
     *
     * @param name  名称，必填
     * @param level 级别，1 到 9
     */
    public record NestedSample(
            @NotBlank(message = "嵌套名称不能为空")
            @Size(max = 16, message = "嵌套名称长度不能超过 16")
            String name,

            @NotNull(message = "嵌套级别不能为空")
            @Min(value = 1, message = "嵌套级别不能小于 1")
            @Max(value = 9, message = "嵌套级别不能超过 9")
            Integer level) {
    }
}
