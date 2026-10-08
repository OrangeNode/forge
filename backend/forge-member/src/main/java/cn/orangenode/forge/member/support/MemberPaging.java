package cn.orangenode.forge.member.support;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.config.ForgePageProperties;

import lombok.RequiredArgsConstructor;

/**
 * 普通用户列表复用项目分页配置。
 */
@Component
@RequiredArgsConstructor
public class MemberPaging {
    private final ForgePageProperties properties;

    /**
     * 按统一配置构造分页对象，越界返回 400。
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
