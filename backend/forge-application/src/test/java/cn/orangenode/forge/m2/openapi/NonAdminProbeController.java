package cn.orangenode.forge.m2.openapi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.response.ApiResponse;

/**
 * 管理端分组边界验证用的探针接口。
 *
 * <p>属于测试夹具：提供一个位于 {@code /api/admin/**} 之外的最小接口，用于验证管理端分组
 * 确实按路径前缀收敛，而不是把全部接口都收进文档。不进入生产模块。</p>
 */
@RestController
@RequestMapping("/api/probe/v1/m2")
public class NonAdminProbeController {

    /**
     * 返回固定响应，用于确认该路径不属于管理端分组。
     *
     * @return 统一成功响应
     */
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }
}
