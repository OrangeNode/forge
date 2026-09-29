package cn.orangenode.forge.m2.openapi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.response.ApiResponse;

/**
 * 用户端分组文档验证用的探针接口。
 *
 * <p>属于测试夹具：提供 {@code /api/app/v1/**} 下的最小接口，用于验证 OpenAPI 的 app 分组
 * 确实按路径前缀划分，且不包含管理端接口。不进入生产模块。</p>
 */
@RestController
@RequestMapping("/api/app/v1/m2")
public class AppGroupProbeController {

    /**
     * 返回固定响应，用于确认用户端分组已收录该路径。
     *
     * @return 统一成功响应
     */
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }
}
