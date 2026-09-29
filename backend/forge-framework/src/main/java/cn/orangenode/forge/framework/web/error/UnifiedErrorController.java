package cn.orangenode.forge.framework.web.error;

import java.util.Map;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.response.ErrorCode;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 框架默认错误出口。
 *
 * <p>容器与过滤器链在进入 DispatcherServlet 前后产生的错误（未知路径、方法不支持、
 * 请求体超限、请求在过滤阶段被拒绝等）最终都会转发到 {@code /error}。
 * 本控制器接管该出口，改用统一错误写出组件输出 HTTP 200 + 对应 {@code body.code}，
 * 不再返回默认错误页面或非 200 状态。</p>
 *
 * <p>DispatcherServlet 内部可预期的异常由全局异常处理器处理，两者复用同一写出组件与错误码语义。</p>
 */
@Slf4j
@RestController
public class UnifiedErrorController implements ErrorController {

    /**
     * 容器记录原始错误状态码的请求属性名。
     */
    private static final String ERROR_STATUS_ATTRIBUTE = RequestDispatcher.ERROR_STATUS_CODE;

    /**
     * 统一错误响应写出组件。
     */
    private final WebErrorWriter errorWriter;

    /**
     * 构造默认错误出口控制器。
     *
     * @param errorWriter 统一错误响应写出组件
     */
    public UnifiedErrorController(WebErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    /**
     * 接管容器错误转发，输出统一失败响应。
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     */
    @RequestMapping("${server.error.path:${error.path:/error}}")
    public void handleError(HttpServletRequest request, HttpServletResponse response) {
        int status = resolveStatus(request);
        log.warn("框架错误出口，traceId={}，原始状态={}，路径={}", errorWriter.resolveTraceId(request), status,
                request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
        errorWriter.write(request, response, mapToBodyCode(status), messageOf(status));
    }

    /**
     * 读取容器记录的原始 HTTP 状态码。
     *
     * @param request 当前 HTTP 请求
     * @return 原始状态码，缺失或非法时按 500 处理
     */
    private int resolveStatus(HttpServletRequest request) {
        Object status = request.getAttribute(ERROR_STATUS_ATTRIBUTE);
        if (status instanceof Integer value) {
            return value;
        }
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    /**
     * 把容器状态码映射为协议约定的响应体业务码。
     *
     * <p>映射表只覆盖协议规定的取值；未列入的状态按未预期错误处理，避免出现规范外的 code。</p>
     *
     * @param status 容器原始状态码
     * @return 协议约定的响应体业务码
     */
    private int mapToBodyCode(int status) {
        return switch (status) {
            case HttpServletResponse.SC_BAD_REQUEST -> ErrorCode.BAD_REQUEST;
            case HttpServletResponse.SC_UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            case HttpServletResponse.SC_FORBIDDEN -> ErrorCode.FORBIDDEN;
            case HttpServletResponse.SC_NOT_FOUND -> ErrorCode.NOT_FOUND;
            case HttpServletResponse.SC_METHOD_NOT_ALLOWED -> ErrorCode.METHOD_NOT_ALLOWED;
            case HttpServletResponse.SC_CONFLICT -> ErrorCode.CONFLICT;
            case HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE -> ErrorCode.PAYLOAD_TOO_LARGE;
            case HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case HttpServletResponse.SC_SERVICE_UNAVAILABLE -> ErrorCode.SERVICE_UNAVAILABLE;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }

    /**
     * 取得与响应体业务码对应的中文说明。
     *
     * <p>说明文案属于实现细节，放在本类的映射表中，避免散落到各业务层。</p>
     *
     * @param status 容器原始状态码
     * @return 面向调用者的中文说明
     */
    private String messageOf(int status) {
        Map<Integer, String> messages = Map.of(
                HttpServletResponse.SC_BAD_REQUEST, "请求参数不正确",
                HttpServletResponse.SC_UNAUTHORIZED, "登录已失效，请重新登录",
                HttpServletResponse.SC_FORBIDDEN, "没有访问该资源的权限",
                HttpServletResponse.SC_NOT_FOUND, "请求的路径不存在",
                HttpServletResponse.SC_METHOD_NOT_ALLOWED, "请求方法不被支持",
                HttpServletResponse.SC_CONFLICT, "数据状态冲突，请刷新后重试",
                HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "上传内容超过允许的大小上限",
                HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "请求的媒体类型不受支持",
                HttpServletResponse.SC_SERVICE_UNAVAILABLE, "服务依赖暂不可用，请稍后重试");
        return messages.getOrDefault(status, "服务器内部错误，请稍后重试");
    }
}
