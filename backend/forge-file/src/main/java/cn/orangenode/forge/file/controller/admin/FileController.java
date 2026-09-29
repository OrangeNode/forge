package cn.orangenode.forge.file.controller.admin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.file.policy.FileStoragePolicy;
import cn.orangenode.forge.file.request.FileRecordPageRequest;
import cn.orangenode.forge.file.response.FileRecordResponse;
import cn.orangenode.forge.file.response.FileUploadResponse;
import cn.orangenode.forge.file.service.FileService;
import cn.orangenode.forge.file.support.FileIds;
import cn.orangenode.forge.framework.audit.AuditOperation;
import cn.orangenode.forge.framework.audit.AuditResourceId;
import cn.orangenode.forge.framework.config.ForgePageProperties;
import cn.orangenode.forge.framework.security.AuthenticatedAdmin;
import cn.orangenode.forge.framework.web.TraceIdFilter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件管理接口。
 *
 * <p>上传、下载、查询与删除都要求权限，当前管理员身份只从安全上下文读取。
 * 所有响应按统一协议返回 HTTP 200：查询、上传与删除的成功结果在 body 中，失败使用非零 body.code。</p>
 *
 * <p>下载成功时返回二进制流并设置安全的 {@code Content-Disposition}（中文文件名使用
 * RFC 5987 编码）；失败发生在开始输出之前时，仍由统一错误出口返回 JSON 错误体，
 * 调用方先识别 JSON 错误再按文件处理。流已开始写出后不再拼接 JSON，只记录日志并中断传输。</p>
 */
@Slf4j
@Tag(name = "文件管理", description = "文件上传、下载、分页查询与删除；响应统一为 HTTP 200，结果由 body.code 表达")
@RestController
@RequestMapping("/api/admin/v1/files")
public class FileController {

    /**
     * 未声明内容类型时下载使用的兜底类型。
     */
    private static final String DEFAULT_CONTENT_TYPE = MediaType.APPLICATION_OCTET_STREAM_VALUE;

    /**
     * 文件用例。
     */
    private final FileService fileService;

    /**
     * 分页配置，提供每页条数上限。
     */
    private final ForgePageProperties pageProperties;

    /**
     * 构造文件管理接口。
     *
     * @param fileService    文件用例
     * @param pageProperties 分页配置
     */
    public FileController(FileService fileService, ForgePageProperties pageProperties) {
        this.fileService = fileService;
        this.pageProperties = pageProperties;
    }

    /**
     * 分页查询文件元数据。
     *
     * @param request     分页与筛选入参
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 文件元数据分页结果
     */
    @Operation(summary = "分页查询文件",
            description = "按文件名包含匹配与存储配置精确匹配筛选，按创建时间倒序；每页条数上限来自 forge.page.max-size")
    @GetMapping
    @PreAuthorize("hasAuthority('file:record:view')")
    public ApiResponse<PageResponse<FileRecordResponse>> page(@Valid FileRecordPageRequest request,
            HttpServletRequest httpRequest) {
        applyPageSizeLimit(request);
        return ApiResponse.successWithTrace(fileService.pageRecords(request),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 上传文件到当前默认存储方案。
     *
     * @param file        上传文件，表单字段名为 {@code file}
     * @param current     当前认证主体，来自安全上下文
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 文件 ID 与安全元数据
     */
    @Operation(summary = "上传文件",
            description = "multipart/form-data，字段名 file；校验扩展名白名单与大小上限，"
                    + "返回文件 ID 与安全元数据；超过大小上限返回 body.code=413，尚未配置默认方案返回 body.code=404")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('file:record:upload')")
    @AuditOperation(action = "file:record:upload", resourceType = "file-record")
    public ApiResponse<FileUploadResponse> upload(@RequestPart(name = "file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedAdmin current, HttpServletRequest httpRequest) {
        Long uploaderId = current == null ? null : current.id();
        String uploaderName = current == null ? null : current.displayName();
        return ApiResponse.successWithTrace(fileService.upload(file, uploaderId, uploaderName),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 按文件 ID 下载文件。
     *
     * @param id          文件 ID，对外字符串形式
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @param response    当前 HTTP 响应，承载二进制流与下载响应头
     */
    @Operation(summary = "下载文件",
            description = "成功时返回二进制流并设置 Content-Disposition；失败发生在流开始前时返回 HTTP 200 与 JSON 错误体，"
                    + "前端先识别约定的 JSON 错误再按文件处理")
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('file:record:download')")
    public void download(@PathVariable("id") String id, HttpServletRequest httpRequest,
            HttpServletResponse response) {
        Long fileId = FileIds.toLong(id, "文件 ID");
        FileRecordResponse record = fileService.getRecord(fileId);
        InputStream content = fileService.openContent(fileId);
        writeAttachment(response, record, content);
    }

    /**
     * 删除文件记录并清理对象。
     *
     * @param id          文件 ID，对外字符串形式
     * @param current     当前认证主体，来自安全上下文
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 不含业务数据的成功响应
     */
    @Operation(summary = "删除文件",
            description = "先清理存储对象再逻辑删除文件记录；对象清理失败返回 body.code=503 且记录保持不变，可重试")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('file:record:delete')")
    @AuditOperation(action = "file:record:delete", resourceType = "file-record")
    public ApiResponse<Void> delete(@PathVariable("id") @AuditResourceId String id,
            @AuthenticationPrincipal AuthenticatedAdmin current, HttpServletRequest httpRequest) {
        fileService.deleteRecord(FileIds.toLong(id, "文件 ID"), current == null ? null : current.id());
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 按分页配置校验每页条数。
     *
     * <p>请求对象由参数绑定创建，字段级配置注入不会生效，因此这里把配置的上限写入请求对象后再校验，
     * 保证 {@code forge.page.max-size} 是每页条数的唯一来源。</p>
     *
     * @param request 分页与筛选入参
     * @throws BusinessException 每页条数超过配置上限时抛出 400
     */
    private void applyPageSizeLimit(FileRecordPageRequest request) {
        request.setConfiguredMaxPageSize(pageProperties.getMaxPageSize());
        if (!request.validatePageSize()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "每页条数不能超过 " + pageProperties.getMaxPageSize());
        }
    }

    /**
     * 写出下载响应。
     *
     * <p>响应头在读取内容之后设置：文件不存在、存储不可用等失败都发生在写出之前，
     * 由统一错误出口返回 JSON 错误体。开始写出后的失败无法再生成 JSON，
     * 只记录日志并中断传输，调用方通过内容长度不一致识别中断。</p>
     *
     * @param response 当前 HTTP 响应
     * @param record   文件元数据，提供文件名、内容类型与大小
     * @param content  文件内容流，由本方法负责关闭
     */
    private void writeAttachment(HttpServletResponse response, FileRecordResponse record, InputStream content) {
        try (InputStream stream = content) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(resolveContentType(record.contentType()));
            response.setContentLengthLong(record.sizeBytes());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment()
                            .filename(record.originalName(), StandardCharsets.UTF_8)
                            .build()
                            .toString());
            stream.transferTo(response.getOutputStream());
            response.flushBuffer();
        } catch (IOException failure) {
            log.error("文件下载中断，fileId={}，响应已开始写出，不再返回统一错误体", record.id());
        }
    }

    /**
     * 解析下载使用的内容类型。
     *
     * <p>只使用上传时声明的安全内容类型：历史记录中可能存在的非法取值一律回退为二进制流类型，
     * 避免把不可信字符写入响应头。</p>
     *
     * @param contentType 记录中的内容类型，允许为空
     * @return 可用于响应头的内容类型
     */
    private String resolveContentType(String contentType) {
        return FileStoragePolicy.isValidContentType(contentType) ? contentType : DEFAULT_CONTENT_TYPE;
    }
}
