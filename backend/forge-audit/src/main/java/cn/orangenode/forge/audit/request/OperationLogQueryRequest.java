package cn.orangenode.forge.audit.request;

import cn.orangenode.forge.framework.page.PageRequest;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 操作日志分页查询入参。
 *
 * <p>页码与每页条数沿用框架的 {@link PageRequest} 语义：默认值与上限来自配置 {@code forge.page}，
 * 越界由请求层的参数校验拒绝。</p>
 *
 * <p>时间范围以带时区的 ISO 8601 字符串提交（例如 {@code 2026-09-29T10:00:00Z}），
 * 由业务层统一解析并在服务层返回 {@code body.code=400}，不在 Controller 里散落日期格式判断。</p>
 */
public class OperationLogQueryRequest extends PageRequest {

    /**
     * 操作者名称快照筛选条件，按包含匹配。
     */
    @Size(max = 64, message = "操作者名称筛选条件长度不能超过 64")
    private String operatorName;

    /**
     * 动作代码筛选条件，例如 {@code system:admin:create}，按精确匹配。
     */
    @Size(max = 64, message = "动作代码筛选条件长度不能超过 64")
    private String action;

    /**
     * 业务结果码筛选条件，{@code 0} 表示成功。
     */
    @Pattern(regexp = "\\d{1,6}", message = "结果码只能是 0 到 999999 的数字")
    private String resultCode;

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
     * 取得操作者名称筛选条件。
     *
     * @return 操作者名称快照，未提供时返回 {@code null}
     */
    public String getOperatorName() {
        return operatorName;
    }

    /**
     * 设置操作者名称筛选条件。
     *
     * @param operatorName 操作者名称快照，按包含匹配
     */
    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    /**
     * 取得动作代码筛选条件。
     *
     * @return 动作代码，未提供时返回 {@code null}
     */
    public String getAction() {
        return action;
    }

    /**
     * 设置动作代码筛选条件。
     *
     * @param action 动作代码，按精确匹配
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * 取得业务结果码筛选条件。
     *
     * @return 结果码字符串，未提供时返回 {@code null}
     */
    public String getResultCode() {
        return resultCode;
    }

    /**
     * 设置业务结果码筛选条件。
     *
     * @param resultCode 结果码字符串，只接受不超过 6 位的数字
     */
    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
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
