package cn.orangenode.forge.app.controller.app;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.app.response.AppHomeResponse;
import cn.orangenode.forge.app.response.AppHotWordsResponse;
import cn.orangenode.forge.app.response.AppLaunchResponse;
import cn.orangenode.forge.app.service.AppPublicService;
import cn.orangenode.forge.app.support.AppValues;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.storage.StoredFileContent;
import cn.orangenode.forge.framework.web.TraceIdFilter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * App 公开配置接口：启动配置与图片匿名可读，首页与热搜需要普通用户令牌。
 */
@Tag(name = "App 首页与配置", description = "launch 与 images 匿名可读；home 与 hot-search 需要普通用户令牌，未登录 body.code=401")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/v1/app")
public class AppPublicController {
    private final AppPublicService service;

    /**
     * 读取启动配置。
     */
    @Operation(summary = "启动配置", description = "当前生效的开屏（无则为空）、VIP 开关、注册开关、客服与公告文字；匿名可读")
    @GetMapping("/launch")
    public ApiResponse<AppLaunchResponse> launch(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.launch(), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 读取首页数据。
     */
    @Operation(summary = "首页数据", description = "在线轮播与推荐行；推荐行影片只含已上架影片，没有影片的行不返回")
    @GetMapping("/home")
    public ApiResponse<AppHomeResponse> home(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.home(), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 读取热搜词。
     */
    @Operation(summary = "热搜词", description = "后台手动热搜词，已排除包含屏蔽词的项")
    @GetMapping("/hot-search")
    public ApiResponse<AppHotWordsResponse> hotSearch(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.hotWords(), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 输出开屏或轮播引用的图片；未被引用的文件返回 JSON 错误 404。
     */
    @Operation(summary = "App 图片", description = "只开放开屏与轮播引用的图片文件，成功为二进制图片，失败为统一 JSON")
    @GetMapping("/images/{id}")
    public void image(@PathVariable String id, HttpServletResponse response) throws IOException {
        StoredFileContent content = service.image(AppValues.id(id, "图片 ID"));
        try (InputStream stream = content.stream()) {
            response.setContentType(content.contentType());
            response.setContentLengthLong(content.sizeBytes());
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=300");
            stream.transferTo(response.getOutputStream());
        }
    }
}
