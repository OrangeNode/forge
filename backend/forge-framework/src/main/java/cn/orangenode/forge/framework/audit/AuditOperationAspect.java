package cn.orangenode.forge.framework.audit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.exception.DependencyUnavailableException;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.security.AuthenticatedAdmin;
import cn.orangenode.forge.framework.web.TraceIdFilter;

import lombok.extern.slf4j.Slf4j;

/**
 * 操作审计切面。
 *
 * <p>在标注 {@link AuditOperation} 的方法执行后记录一条审计记录：成功记 0，可预期失败记异常自带的结果码，
 * 未预期异常记 500。记录内容只有身份快照、动作、对象、结果与追踪编号，不接触请求体。</p>
 *
 * <p>审计写入失败不改变业务结果，只以错误日志留痕：审计属于旁路能力，不能因为审计库短暂不可用
 * 就让已经成功的业务操作对外表现为失败。审计模块未装配时同样跳过并记录调试日志。</p>
 */
@Slf4j
@Aspect
@Component
public class AuditOperationAspect {

    /**
     * 审计记录端口，允许审计模块未装配。
     */
    private final ObjectProvider<AuditRecorder> recorderProvider;

    /**
     * 构造操作审计切面。
     *
     * @param recorderProvider 审计记录端口提供者
     */
    public AuditOperationAspect(ObjectProvider<AuditRecorder> recorderProvider) {
        this.recorderProvider = recorderProvider;
    }

    /**
     * 环绕标注了审计注解的方法并记录结果。
     *
     * @param joinPoint      被拦截的方法
     * @param auditOperation 审计注解
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的异常原样透传
     */
    @Around("@annotation(auditOperation)")
    public Object around(ProceedingJoinPoint joinPoint, AuditOperation auditOperation) throws Throwable {
        int resultCode = ApiResponse.SUCCESS_CODE;
        try {
            return joinPoint.proceed();
        } catch (Throwable failure) {
            resultCode = resolveResultCode(failure);
            throw failure;
        } finally {
            writeRecord(joinPoint, auditOperation, resultCode);
        }
    }

    /**
     * 写出审计记录。
     *
     * @param joinPoint      被拦截的方法
     * @param auditOperation 审计注解
     * @param resultCode     业务结果 code
     */
    private void writeRecord(ProceedingJoinPoint joinPoint, AuditOperation auditOperation, int resultCode) {
        AuditRecorder recorder = recorderProvider.getIfAvailable();
        if (recorder == null) {
            log.debug("审计模块未装配，跳过操作审计，action={}", auditOperation.action());
            return;
        }
        AuthenticatedAdmin operator = currentAdmin();
        AuditOperationRecord record = new AuditOperationRecord(
                operator == null ? "SYSTEM" : "ADMIN",
                operator == null ? null : operator.id(),
                operator == null ? null : operator.displayName(),
                auditOperation.action(),
                auditOperation.resourceType(),
                resolveResourceId(joinPoint),
                resultCode,
                currentTraceId());
        try {
            recorder.record(record);
        } catch (RuntimeException failure) {
            log.error("操作审计写入失败，action={}，resultCode={}", auditOperation.action(), resultCode, failure);
        }
    }

    /**
     * 把异常映射为对外结果码。
     *
     * <p>映射与响应侧保持一致：业务异常、依赖不可用与权限拒绝各取自身结果码，
     * 数据库唯一键与完整性冲突对应 409，Redis 连接失败与数据源连接失败对应 503，
     * 其余未预期异常对应 500。两侧必须同码，否则操作日志会把 409 记成 500，
     * 让审计结果与调用方看到的响应不一致。</p>
     *
     * @param failure 业务方法抛出的异常
     * @return 结果码，未预期异常返回 500
     */
    private int resolveResultCode(Throwable failure) {
        if (failure instanceof BusinessException businessException) {
            return businessException.getCode();
        }
        if (failure instanceof DependencyUnavailableException dependencyException) {
            return dependencyException.getCode();
        }
        if (failure instanceof AccessDeniedException) {
            return ErrorCode.FORBIDDEN;
        }
        if (failure instanceof DuplicateKeyException || failure instanceof DataIntegrityViolationException) {
            return ErrorCode.CONFLICT;
        }
        if (failure instanceof RedisConnectionFailureException
                || failure instanceof DataAccessResourceFailureException) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }
        return ErrorCode.INTERNAL_ERROR;
    }

    /**
     * 读取当前认证主体。
     *
     * @return 管理员主体，匿名或系统调用时返回 {@code null}
     */
    private AuthenticatedAdmin currentAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedAdmin admin) {
            return admin;
        }
        return null;
    }

    /**
     * 读取被 {@link AuditResourceId} 标注的参数值。
     *
     * @param joinPoint 被拦截的方法
     * @return 对象 ID 字符串，未标注或值为空时返回 {@code null}
     */
    private String resolveResourceId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        Object[] arguments = joinPoint.getArgs();
        for (int index = 0; index < parameterAnnotations.length && index < arguments.length; index++) {
            for (Annotation annotation : parameterAnnotations[index]) {
                if (annotation instanceof AuditResourceId && arguments[index] != null) {
                    return String.valueOf(arguments[index]);
                }
            }
        }
        return null;
    }

    /**
     * 读取当前请求的追踪编号。
     *
     * @return 追踪编号，无请求上下文时返回 {@code null}
     */
    private String currentTraceId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return TraceIdFilter.currentTraceId(attributes.getRequest());
        }
        return null;
    }
}
