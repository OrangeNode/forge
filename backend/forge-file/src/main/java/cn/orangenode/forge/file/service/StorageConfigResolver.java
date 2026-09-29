package cn.orangenode.forge.file.service;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.file.entity.FileStorageConfigEntity;
import cn.orangenode.forge.file.entity.FileStorageDefaultEntity;
import cn.orangenode.forge.file.mapper.FileStorageConfigMapper;
import cn.orangenode.forge.file.mapper.FileStorageDefaultMapper;
import cn.orangenode.forge.file.policy.FileStoragePolicy;
import cn.orangenode.forge.framework.config.ForgeFileProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 默认存储方案解析组件。
 *
 * <p>模块内部组件，不属于对外业务接口：上传用例与存储配置用例都需要知道“当前默认方案”，
 * 解析规则放在这里，避免两处各写一套而出现不一致。</p>
 *
 * <p>解析顺序是：先取 {@code file_storage_default} 的单行指针（{@code id = 1}）；
 * 指针不存在或指向的配置已不可用时，回退到配置 {@code forge.file.default-config-code}
 * 指定方案代码的最新有效版本。回退只影响默认方案的解析结果，不会写回指针，
 * 管理员显式切换默认后指针始终优先。</p>
 */
@Slf4j
@Component
public class StorageConfigResolver {

    /**
     * 默认指针固定主键：指针表全表只有这一行。
     */
    public static final long DEFAULT_POINTER_ID = 1L;

    /**
     * 存储配置数据访问。
     */
    private final FileStorageConfigMapper configMapper;

    /**
     * 默认指针数据访问。
     */
    private final FileStorageDefaultMapper defaultMapper;

    /**
     * 文件与存储配置，提供默认方案代码。
     */
    private final ForgeFileProperties properties;

    /**
     * 构造默认存储方案解析组件。
     *
     * @param configMapper  存储配置数据访问
     * @param defaultMapper 默认指针数据访问
     * @param properties    文件与存储配置
     */
    public StorageConfigResolver(FileStorageConfigMapper configMapper, FileStorageDefaultMapper defaultMapper,
            ForgeFileProperties properties) {
        this.configMapper = configMapper;
        this.defaultMapper = defaultMapper;
        this.properties = properties;
    }

    /**
     * 解析当前默认存储配置版本。
     *
     * @return 默认配置实体，没有任何可用方案时返回 {@code null}
     */
    public FileStorageConfigEntity resolveDefault() {
        FileStorageDefaultEntity pointer = defaultMapper.selectById(DEFAULT_POINTER_ID);
        if (pointer != null && pointer.getConfigId() != null) {
            FileStorageConfigEntity config = configMapper.selectById(pointer.getConfigId());
            if (config != null) {
                return config;
            }
            log.warn("默认存储指针指向的配置已不可用，按默认方案代码重新解析，pointerConfigId={}",
                    pointer.getConfigId());
        }
        return resolveByDefaultCode();
    }

    /**
     * 解析当前默认存储配置版本，不存在时抛出业务异常。
     *
     * @return 默认配置实体
     * @throws BusinessException 尚未配置任何可用方案时抛出 404
     */
    public FileStorageConfigEntity requireDefault() {
        FileStorageConfigEntity config = resolveDefault();
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "尚未配置默认存储方案，请先创建存储配置");
        }
        return config;
    }

    /**
     * 解析当前默认存储配置版本 ID。
     *
     * @return 默认配置 ID，没有任何可用方案时返回 {@code null}
     */
    public Long resolveDefaultId() {
        FileStorageConfigEntity config = resolveDefault();
        return config == null ? null : config.getId();
    }

    /**
     * 判断指定配置版本是否为当前默认方案。
     *
     * @param configId 存储配置版本 ID，允许为 {@code null}
     * @return 是当前默认方案时返回 {@code true}
     */
    public boolean isDefault(Long configId) {
        if (configId == null) {
            return false;
        }
        return configId.equals(resolveDefaultId());
    }

    /**
     * 按默认方案代码解析最新有效版本。
     *
     * @return 最新有效版本，未配置默认方案代码或该代码没有有效版本时返回 {@code null}
     */
    private FileStorageConfigEntity resolveByDefaultCode() {
        String code = FileStoragePolicy.normalizeCode(properties.getDefaultConfigCode());
        if (code == null) {
            return null;
        }
        return configMapper.selectLatestByCode(code);
    }
}
