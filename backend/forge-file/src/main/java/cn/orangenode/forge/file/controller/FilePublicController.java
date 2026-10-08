package cn.orangenode.forge.file.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.file.policy.FileStoragePolicy;
import cn.orangenode.forge.file.response.FileRecordResponse;
import cn.orangenode.forge.file.service.FileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 公开外链下载接口，匿名可用（路径在 {@code forge.security.permit-all-paths} 中放行）。
 *
 * <p>只按公开令牌查找文件，路径末尾的文件名不参与查找。支持单段 {@code Range: bytes=起-止}
 * 请求，返回 206 与 {@code Content-Range}，供 App 断点续传；令牌无效时在写出前由统一错误出口返回
 * HTTP 200 与 JSON 错误体（body.code=404），下载方需先识别 JSON 错误再按文件处理。</p>
 */
@Slf4j
@Tag(name = "公开外链", description = "按公开令牌匿名下载文件，支持断点续传")
@RestController
@RequestMapping("/api/public/v1/files")
public class FilePublicController {

    /**
     * 单段字节范围请求格式。
     */
    private static final Pattern RANGE = Pattern.compile("^bytes=(\\d+)-(\\d*)$");

    /**
     * 复制内容时的缓冲区大小。
     */
    private static final int BUFFER_SIZE = 64 * 1024;

    /**
     * 文件用例。
     */
    private final FileService fileService;

    /**
     * 构造公开外链下载接口。
     *
     * @param fileService 文件用例
     */
    public FilePublicController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 按公开令牌下载文件。
     *
     * @param token    公开令牌
     * @param name     文件名，只用于下载器显示，不参与查找
     * @param range    可选的字节范围请求头
     * @param response 当前 HTTP 响应
     */
    @Operation(summary = "公开下载", description = "支持单段 Range 断点续传；令牌无效或已撤销时返回 body.code=404")
    @GetMapping("/{token}/{name}")
    public void download(@PathVariable("token") String token, @PathVariable("name") String name,
            @RequestHeader(name = HttpHeaders.RANGE, required = false) String range, HttpServletResponse response) {
        FileRecordResponse record = fileService.getPublicRecord(token);
        long size = record.sizeBytes();
        long start = 0;
        long end = size - 1;
        boolean partial = false;
        Matcher matcher = range == null ? null : RANGE.matcher(range.strip());
        if (matcher != null && matcher.matches()) {
            start = Long.parseLong(matcher.group(1));
            if (!matcher.group(2).isEmpty()) {
                end = Math.min(end, Long.parseLong(matcher.group(2)));
            }
            if (start >= size || start > end) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + size);
                return;
            }
            partial = true;
        }
        InputStream content = fileService.openContent(Long.valueOf(record.id()));
        write(response, record, content, start, end, partial);
    }

    /**
     * 写出全部或部分文件内容。
     *
     * <p>响应头在内容流打开之后设置，打开失败仍由统一错误出口返回 JSON；开始写出后的失败只记录日志并中断，
     * 下载方通过长度或校验值不一致识别。</p>
     *
     * @param response 当前 HTTP 响应
     * @param record   文件元数据
     * @param content  文件内容流，由本方法负责关闭
     * @param start    起始字节（含）
     * @param end      结束字节（含）
     * @param partial  是否按范围返回 206
     */
    private void write(HttpServletResponse response, FileRecordResponse record, InputStream content, long start,
            long end, boolean partial) {
        try (InputStream stream = content) {
            long length = end - start + 1;
            response.setStatus(partial ? HttpServletResponse.SC_PARTIAL_CONTENT : HttpServletResponse.SC_OK);
            response.setContentType(FileStoragePolicy.isValidContentType(record.contentType())
                    ? record.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
            response.setContentLengthLong(Math.max(length, 0));
            if (partial) {
                response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + record.sizeBytes());
            }
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename(record.originalName(), StandardCharsets.UTF_8).build().toString());
            stream.skipNBytes(start);
            copy(stream, response.getOutputStream(), length);
            response.flushBuffer();
        } catch (IOException failure) {
            log.warn("公开下载中断，fileId={}，响应已开始写出", record.id());
        }
    }

    /**
     * 从输入流复制指定字节数到输出流。
     *
     * @param in     输入流
     * @param out    输出流
     * @param length 需要复制的字节数
     * @throws IOException 读写失败
     */
    private void copy(InputStream in, OutputStream out, long length) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = length;
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                break;
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }
}
