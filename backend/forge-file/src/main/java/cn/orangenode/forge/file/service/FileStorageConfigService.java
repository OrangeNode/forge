package cn.orangenode.forge.file.service;

import java.util.List;

import cn.orangenode.forge.file.request.StorageConfigCreateRequest;
import cn.orangenode.forge.file.request.StorageConfigUpdateRequest;
import cn.orangenode.forge.file.response.StorageConfigResponse;
import cn.orangenode.forge.file.response.StorageTestResponse;

/**
 * 文件存储配置用例。
 *
 * <p>面向管理端的存储方案管理：版本化创建、未引用版本的修改、删除保护、连接检测与默认方案切换。
 * 目标（相对目录、访问地址、桶名称）变化一律创建新版本；被文件引用的版本不可原地修改与删除，
 * 切换默认只影响之后的新上传，历史文件仍按记录里固定的版本读取。</p>
 *
 * <p>对外只暴露请求与响应类型，不暴露 Entity 与 Mapper；凭据只以“是否已配置”的形式出现在响应里。
 * 可预期失败抛业务异常，由统一错误出口转换为 {@code body.code}：参数不合法 400、配置不存在 404、
 * 已被引用或仍是默认方案 409、依赖不可用 503。</p>
 */
public interface FileStorageConfigService {

    /**
     * 查询全部存储方案与版本。
     *
     * @return 按方案代码升序、版本倒序排列的配置列表，并标记当前默认方案
     */
    List<StorageConfigResponse> listConfigs();

    /**
     * 查询当前默认存储方案，尚未配置可用方案时返回 {@code body.code=404}。
     *
     * @return 当前默认配置
     */
    StorageConfigResponse getDefaultConfig();

    /**
     * 创建存储方案的新版本。
     *
     * <p>同一代码下版本号按已有最大版本递增，因此目标变化通过新增版本表达；
     * 单文件大小上限不得高于应用上传硬上限，版本号并发冲突由数据库唯一键拦截并返回
     * {@code body.code=409}。</p>
     *
     * @param request    新增入参
     * @param operatorId 操作管理员 ID，允许为 {@code null} 表示系统操作
     * @return 新版本配置
     */
    StorageConfigResponse createConfig(StorageConfigCreateRequest request, Long operatorId);

    /**
     * 修改尚未被文件引用的存储配置版本。
     *
     * <p>已被引用时返回 {@code body.code=409} 并提示创建新版本；凭据留空表示保持原凭据。</p>
     *
     * @param id         存储配置版本 ID
     * @param request    修改入参
     * @param operatorId 操作管理员 ID，允许为 {@code null} 表示系统操作
     * @return 修改后的配置
     */
    StorageConfigResponse updateConfig(Long id, StorageConfigUpdateRequest request, Long operatorId);

    /**
     * 逻辑删除尚未被引用且不是默认方案的存储配置版本。
     *
     * <p>被文件引用或仍是默认方案时返回 {@code body.code=409}，不做隐式切换或级联处理。</p>
     *
     * @param id         存储配置版本 ID
     * @param operatorId 操作管理员 ID，允许为 {@code null} 表示系统操作
     */
    void deleteConfig(Long id, Long operatorId);

    /**
     * 检测存储目标是否可用。
     *
     * <p>检测结论通过返回值表达而不是抛异常：目标不可用同样是一次成功的检测，
     * 由 {@code available=false} 与中文说明告知使用者。</p>
     *
     * @param id 存储配置版本 ID
     * @return 检测耗时与结果
     */
    StorageTestResponse testConnection(Long id);

    /**
     * 切换默认存储方案。
     *
     * <p>只更新默认指针单行，改动只影响之后的新上传，不改变已写入文件的读取位置。</p>
     *
     * @param id         存储配置版本 ID
     * @param operatorId 操作管理员 ID，允许为 {@code null} 表示系统操作
     */
    void switchDefault(Long id, Long operatorId);
}
