package cn.orangenode.forge.member.controller.app;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.web.TraceIdFilter;
import cn.orangenode.forge.member.request.MemberHistoryRequest;
import cn.orangenode.forge.member.response.MemberFavoriteResponse;
import cn.orangenode.forge.member.response.MemberFavoriteStatusResponse;
import cn.orangenode.forge.member.response.MemberHistoryResponse;
import cn.orangenode.forge.member.service.MemberLibraryService;
import cn.orangenode.forge.member.support.MemberValues;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * App 观看历史与收藏接口，全部需要普通用户令牌，不要求 VIP；手机与电视共用同一份数据。
 */
@Tag(name = "App 历史与收藏", description = "需要普通用户令牌；已下架影片在列表中被跳过")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/v1/member")
public class MemberLibraryController {
    private final MemberLibraryService service;

    /**
     * 分页读取观看历史。
     */
    @Operation(summary = "观看历史", description = "按最近观看时间倒序；含最近一集与播放位置，用于首页“继续观看”")
    @GetMapping("/history")
    public ApiResponse<PageResponse<MemberHistoryResponse>> history(@RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.history(pageNum, pageSize), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 读取单部影片的观看进度。
     */
    @Operation(summary = "单部影片观看进度", description = "用于断点续播；没有记录时 data 为空")
    @GetMapping("/history/{videoId}")
    public ApiResponse<MemberHistoryResponse> progress(@PathVariable String videoId, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.progress(MemberValues.id(videoId)).orElse(null),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 上报观看进度。
     */
    @Operation(summary = "上报观看进度", description = "播放中定时上报；同一影片只保留最近一次；影片不存在或已下架 404")
    @PutMapping("/history/{videoId}")
    public ApiResponse<Void> saveProgress(@PathVariable String videoId, @Valid @RequestBody MemberHistoryRequest request,
            HttpServletRequest http) {
        service.saveProgress(MemberValues.id(videoId), request);
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 删除一条观看历史。
     */
    @Operation(summary = "删除观看历史", description = "不存在时同样返回成功")
    @DeleteMapping("/history/{videoId}")
    public ApiResponse<Void> deleteHistory(@PathVariable String videoId, HttpServletRequest http) {
        service.deleteHistory(MemberValues.id(videoId));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 清空观看历史。
     */
    @Operation(summary = "清空观看历史")
    @DeleteMapping("/history")
    public ApiResponse<Void> clearHistory(HttpServletRequest http) {
        service.clearHistory();
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 分页读取收藏。
     */
    @Operation(summary = "我的收藏", description = "按收藏时间倒序")
    @GetMapping("/favorites")
    public ApiResponse<PageResponse<MemberFavoriteResponse>> favorites(@RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.favorites(pageNum, pageSize), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 查询是否已收藏。
     */
    @Operation(summary = "是否已收藏")
    @GetMapping("/favorites/{videoId}")
    public ApiResponse<MemberFavoriteStatusResponse> favorited(@PathVariable String videoId, HttpServletRequest http) {
        return ApiResponse.successWithTrace(new MemberFavoriteStatusResponse(service.favorited(MemberValues.id(videoId))),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 收藏影片。
     */
    @Operation(summary = "收藏影片", description = "重复收藏返回成功；影片不存在或已下架 404")
    @PutMapping("/favorites/{videoId}")
    public ApiResponse<Void> addFavorite(@PathVariable String videoId, HttpServletRequest http) {
        service.addFavorite(MemberValues.id(videoId));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 取消收藏。
     */
    @Operation(summary = "取消收藏", description = "未收藏时同样返回成功")
    @DeleteMapping("/favorites/{videoId}")
    public ApiResponse<Void> removeFavorite(@PathVariable String videoId, HttpServletRequest http) {
        service.removeFavorite(MemberValues.id(videoId));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }
}
