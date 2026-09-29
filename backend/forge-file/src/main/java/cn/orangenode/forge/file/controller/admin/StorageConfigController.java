package cn.orangenode.forge.file.controller.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.file.request.StorageConfigCreateRequest;
import cn.orangenode.forge.file.request.StorageConfigUpdateRequest;
import cn.orangenode.forge.file.response.StorageConfigResponse;
import cn.orangenode.forge.file.response.StorageTestResponse;
import cn.orangenode.forge.file.service.FileStorageConfigService;
import cn.orangenode.forge.file.support.FileIds;
import cn.orangenode.forge.framework.audit.AuditOperation;
import cn.orangenode.forge.framework.audit.AuditResourceId;
import cn.orangenode.forge.framework.security.AuthenticatedAdmin;
import cn.orangenode.forge.framework.web.TraceIdFilter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 存储配置管理接口。
 *
 * <p>管理存储方案与版本、默认方案、连接检测与删除保护。所有响应统一为 HTTP 200：
 * 成功 {@code body.code=0}，参数不合法 400、资源不存在 404、冲突（已被引用或仍是默认方案）409、
 * 存储依赖不可用 503；写接口都声明操作审计，路径参数作为审计对象 ID。</p>
 *
 * <p>凭据只以布尔值形式回显“是否已配置”，明文与密文都不出现在响应、日志与审计里。
 * 每个接口单独声明权限码，权限拒绝由统一出口写成 {@code body.code=403}。</p>
 */
@Tag(name = "存储配置", description = "存储方案版本、默认方案切换、连接检测与删除保护；响应统一为 HTTP 200，结果由 body.code 表达")
@RestController
@RequestMapping("/api/admin/v1/storage-configs")
public class StorageConfigController {

    /**
     * 存储配置用例。
     */
    private final FileStorageConfigService storageConfigService;

    /**
     * 构造存储配置管理接口。
     *
     * @param storageConfigService 存储配置用例
     */
    public StorageConfigController(FileStorageConfigService storageConfigService) {
        this.storageConfigService = storageConfigService;
    }

    /**
     * 查询全部存储方案与版本。
     *
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 存储配置列表，含当前默认标记
     */
    @Operation(summary = "查询存储配置列表",
            description = "返回全部方案与版本，按方案代码升序、版本倒序，并标记当前默认方案；不返回凭据")
    @GetMapping
    @PreAuthorize("hasAuthority('file:storage:view')")
    public ApiResponse<List<StorageConfigResponse>> list(HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(storageConfigService.listConfigs(),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 查询当前默认存储方案。
     *
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 当前默认配置，尚未配置时统一错误出口返回 {@code body.code=404}
     */
    @Operation(summary = "查询当前默认方案",
            description = "返回当前默认存储配置；默认指针缺失时按 forge.file.default-config-code 解析，仍未配置返回 body.code=404")
    @GetMapping("/default")
    @PreAuthorize("hasAuthority('file:storage:view')")
    public ApiResponse<StorageConfigResponse> defaultConfig(HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(storageConfigService.getDefaultConfig(),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 创建存储方案的新版本。
     *
     * @param request     新增入参
     * @param current     当前认证主体，来自安全上下文
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 新版本配置
     */
    @Operation(summary = "新增存储配置版本",
            description = "同代码下版本号自动递增；local 必填相对目录，s3 必填访问地址、桶名称与凭据；"
                    + "单文件大小上限不得高于应用硬上限，否则返回 body.code=400")
    @PostMapping
    @PreAuthorize("hasAuthority('file:storage:create')")
    @AuditOperation(action = "file:storage:create", resourceType = "storage-config")
    public ApiResponse<StorageConfigResponse> create(@Valid @RequestBody StorageConfigCreateRequest request,
            @AuthenticationPrincipal AuthenticatedAdmin current, HttpServletRequest httpRequest) {
        StorageConfigResponse response = storageConfigService.createConfig(request, currentAdminId(current));
        return ApiResponse.successWithTrace(response, TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 修改尚未被文件引用的存储配置版本。
     *
     * @param id          存储配置版本 ID，对外字符串形式
     * @param request     修改入参
     * @param current     当前认证主体，来自安全上下文
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 修改后的配置
     */
    @Operation(summary = "修改存储配置",
            description = "只允许修改尚未被文件引用的版本；已被引用返回 body.code=409 并提示创建新版本；"
                    + "凭据留空表示保持原凭据")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('file:storage:update')")
    @AuditOperation(action = "file:storage:update", resourceType = "storage-config")
    public ApiResponse<StorageConfigResponse> update(@PathVariable("id") @AuditResourceId String id,
            @Valid @RequestBody StorageConfigUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedAdmin current, HttpServletRequest httpRequest) {
        StorageConfigResponse response = storageConfigService.updateConfig(FileIds.toLong(id, "存储配置 ID"),
                request, currentAdminId(current));
        return ApiResponse.successWithTrace(response, TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 删除未被引用且不是默认方案的存储配置版本。
     *
     * @param id          存储配置版本 ID，对外字符串形式
     * @param current     当前认证主体，来自安全上下文
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 不含业务数据的成功响应
     */
    @Operation(summary = "删除存储配置版本",
            description = "逻辑删除；已被文件引用或仍是默认方案时返回 body.code=409，需先切换默认方案")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('file:storage:delete')")
    @AuditOperation(action = "file:storage:delete", resourceType = "storage-config")
    public ApiResponse<Void> delete(@PathVariable("id") @AuditResourceId String id,
            @AuthenticationPrincipal AuthenticatedAdmin current, HttpServletRequest httpRequest) {
        storageConfigService.deleteConfig(FileIds.toLong(id, "存储配置 ID"), currentAdminId(current));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 检测存储目标是否可用。
     *
     * @param id          存储配置版本 ID，对外字符串形式
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 检测耗时与结果，目标不可用时 {@code available=false} 而不是抛出失败
     */
    @Operation(summary = "检测存储连接",
            description = "构造存储适配并检测目标可用性，返回耗时与结果；目标不可用时 available=false，"
                    + "提示不包含访问地址与凭据")
    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('file:storage:test')")
    @AuditOperation(action = "file:storage:test", resourceType = "storage-config")
    public ApiResponse<StorageTestResponse> test(@PathVariable("id") @AuditResourceId String id,
            HttpServletRequest httpRequest) {
        StorageTestResponse response = storageConfigService.testConnection(FileIds.toLong(id, "存储配置 ID"));
        return ApiResponse.successWithTrace(response, TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 切换默认存储方案。
     *
     * @param id          存储配置版本 ID，对外字符串形式
     * @param current     当前认证主体，来自安全上下文
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 不含业务数据的成功响应
     */
    @Operation(summary = "切换默认存储方案",
            description = "更新默认指针单行，只影响之后的新上传；历史文件仍按记录中固定的配置版本读取")
    @PutMapping("/{id}/default")
    @PreAuthorize("hasAuthority('file:storage:default')")
    @AuditOperation(action = "file:storage:default", resourceType = "storage-config")
    public ApiResponse<Void> switchDefault(@PathVariable("id") @AuditResourceId String id,
            @AuthenticationPrincipal AuthenticatedAdmin current, HttpServletRequest httpRequest) {
        storageConfigService.switchDefault(FileIds.toLong(id, "存储配置 ID"), currentAdminId(current));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 读取当前管理员 ID。
     *
     * @param current 当前认证主体，允许为 {@code null}
     * @return 管理员 ID，未认证时返回 {@code null}
     */
    private Long currentAdminId(AuthenticatedAdmin current) {
        return current == null ? null : current.id();
    }
}
