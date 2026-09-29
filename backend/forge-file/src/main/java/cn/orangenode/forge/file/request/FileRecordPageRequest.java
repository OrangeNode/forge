package cn.orangenode.forge.file.request;

import cn.orangenode.forge.file.policy.FileStoragePolicy;
import cn.orangenode.forge.framework.page.PageRequest;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 文件记录分页查询入参。
 *
 * <p>页码与每页条数沿用框架的 {@link PageRequest} 语义：默认值来自配置 {@code forge.page}，
 * 每页条数上限由 Controller 从同一配置注入后再校验，越界按参数错误拒绝。</p>
 *
 * <p>两个筛选条件都是可选的：文件名按包含匹配，存储配置 ID 按精确匹配。
 * ID 使用对外字符串形式，非法格式在业务层统一返回 {@code body.code=400}。</p>
 */
public class FileRecordPageRequest extends PageRequest {

    /**
     * 原始文件名筛选条件，按包含匹配。
     */
    @Size(max = FileStoragePolicy.ORIGINAL_NAME_MAX_LENGTH, message = "文件名筛选条件长度不能超过 255")
    private String originalName;

    /**
     * 存储配置版本 ID 筛选条件，对外字符串形式。
     */
    @Pattern(regexp = "\\d{1,19}", message = "存储配置 ID 只能是正整数")
    private String storageConfigId;

    /**
     * 取得原始文件名筛选条件。
     *
     * @return 文件名关键字，未提供时返回 {@code null}
     */
    public String getOriginalName() {
        return originalName;
    }

    /**
     * 设置原始文件名筛选条件。
     *
     * @param originalName 文件名关键字，按包含匹配
     */
    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    /**
     * 取得存储配置版本 ID 筛选条件。
     *
     * @return 字符串形式 ID，未提供时返回 {@code null}
     */
    public String getStorageConfigId() {
        return storageConfigId;
    }

    /**
     * 设置存储配置版本 ID 筛选条件。
     *
     * @param storageConfigId 字符串形式 ID，只接受正整数
     */
    public void setStorageConfigId(String storageConfigId) {
        this.storageConfigId = storageConfigId;
    }
}
