package cn.orangenode.forge.example.controller.admin;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.example.response.HelloResponse;

/**
 * 示例模块的健康检查接口。
 *
 * <p>只用于验证模块装配、统一响应与可移除性；不建业务表，也不承载业务流程。
 * 移除示例模块时一并删除本类与所在模块。</p>
 */
@RestController
@RequestMapping("/api/admin/v1/example")
public class HelloController {

    /**
     * 返回示例响应，用于确认应用已装配并可对外提供接口。
     *
     * @return 包含服务名与服务器时间的统一成功响应
     */
    @GetMapping("/hello")
    public ApiResponse<HelloResponse> hello() {
        return ApiResponse.ok(new HelloResponse("orange-forge", Instant.now()));
    }
}
