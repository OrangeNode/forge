package cn.orangenode.forge.audit.request;

import cn.orangenode.forge.framework.page.PageRequest;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 登录日志分页查询入参。
 *
 * <p>页码与每页条数沿用框架的 {@link PageRequest} 语义：默认值与上限来自配置 {@code forge.page}，
 * 越界由请求层的参数校验拒绝，不在业务代码里写死条数。</p>
 *
 * <p>时间范围以带时区的 ISO 8601 字符串提交（例如 {@code 2026-09-29T10:00:00Z}），
 * 由业务层统一解析：查询参数在绑定阶段无法做时区语义校验，因此解析与格式校验集中在服务层，
 * 失败返回 {@code body.code=400}。</p>
 */
public class LoginLogQueryRequest extends PageRequest {

    /**
     * 用户名筛选条件，按包含匹配，不区分大小写。
     */
    @Size(max = 64, message = "用户名筛选条件长度不能超过 64")
    private String username;

    /**
     * 登录结果筛选条件：{@code success} 成功，{@code failure} 失败。
     */
    @Pattern(regexp = "success|failure", message = "登录结果只能是 success 或 failure")
    private String result;

    /**
     * 起始时间（含），带时区的 ISO 8601 字符串。
     */
    @Size(max = 40, message = "起始时间长度不能超过 40")
    private String startTime;

    /**
     * 结束时间（含），带时区的 ISO 8601 字符串。
     */
    @Size(max = 40, message = "结束时间长度不能超过 40")
    private String endTime;

    /**
     * 取得用户名筛选条件。
     *
     * @return 用户名，未提供时返回 {@code null}
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置用户名筛选条件。
     *
     * @param username 用户名，按包含匹配
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 取得登录结果筛选条件。
     *
     * @return 结果为 {@code success} 或 {@code failure}，未提供时返回 {@code null}
     */
    public String getResult() {
        return result;
    }

    /**
     * 设置登录结果筛选条件。
     *
     * @param result 登录结果，只接受 {@code success} 或 {@code failure}
     */
    public void setResult(String result) {
        this.result = result;
    }

    /**
     * 取得起始时间字符串。
     *
     * @return 带时区的 ISO 8601 字符串，未提供时返回 {@code null}
     */
    public String getStartTime() {
        return startTime;
    }

    /**
     * 设置起始时间字符串。
     *
     * @param startTime 带时区的 ISO 8601 字符串
     */
    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    /**
     * 取得结束时间字符串。
     *
     * @return 带时区的 ISO 8601 字符串，未提供时返回 {@code null}
     */
    public String getEndTime() {
        return endTime;
    }

    /**
     * 设置结束时间字符串。
     *
     * @param endTime 带时区的 ISO 8601 字符串
     */
    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
}
