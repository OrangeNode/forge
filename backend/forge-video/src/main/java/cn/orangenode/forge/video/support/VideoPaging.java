package cn.orangenode.forge.video.support;

import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.orangenode.forge.framework.config.ForgePageProperties;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import lombok.RequiredArgsConstructor;

/**
 * 视频列表复用项目分页配置。
 */
@Component
@RequiredArgsConstructor
public class VideoPaging {
    private final ForgePageProperties properties;

    /**
     * 按统一配置构造本次查询的分页对象。
     */
    public <T> Page<T> page(Integer pageNum, Integer pageSize) {
        int number = pageNum == null ? properties.getDefaultPageNum() : pageNum;
        int size = pageSize == null ? properties.getDefaultPageSize() : pageSize;
        if (number < 1 || size < 1 || size > properties.getMaxPageSize()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分页参数超出允许范围");
        }
        return new Page<>(number, size);
    }
}
