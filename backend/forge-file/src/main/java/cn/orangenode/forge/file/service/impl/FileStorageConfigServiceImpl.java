package cn.orangenode.forge.file.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.file.converter.StorageConfigConverter;
import cn.orangenode.forge.file.entity.FileStorageConfigEntity;
import cn.orangenode.forge.file.entity.FileStorageDefaultEntity;
import cn.orangenode.forge.file.mapper.FileRecordMapper;
import cn.orangenode.forge.file.mapper.FileStorageConfigMapper;
import cn.orangenode.forge.file.mapper.FileStorageDefaultMapper;
import cn.orangenode.forge.file.policy.FileStoragePolicy;
import cn.orangenode.forge.file.request.StorageConfigCreateRequest;
import cn.orangenode.forge.file.request.StorageConfigUpdateRequest;
import cn.orangenode.forge.file.response.StorageConfigResponse;
import cn.orangenode.forge.file.response.StorageTestResponse;
import cn.orangenode.forge.file.service.FileStorageConfigService;
import cn.orangenode.forge.file.service.StorageConfigResolver;
import cn.orangenode.forge.file.storage.StorageProviderFactory;
import cn.orangenode.forge.framework.config.ForgeFileProperties;
import cn.orangenode.forge.framework.crypto.CredentialCipher;
import cn.orangenode.forge.framework.storage.StorageProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件存储配置用例实现。
 *
 * <p>版本化规则集中在这里：同一方案代码下新版本取已有最大版本加一（唯一键 {@code (code, version)}
 * 不含逻辑删除标记，因此已删除版本仍占用版本号），目标变化一律新增版本，不原地改写历史版本。</p>
 *
 * <p>保护规则：已被文件记录引用的版本不允许原地修改（包括逻辑删除的记录，因为它仍引用该版本），
 * 也不允许删除；当前默认方案不允许删除，必须先切换默认。默认方案切换只更新单行指针，
 * 之后的新上传使用新目标，历史文件继续按记录中固定的版本读取。</p>
 *
 * <p>凭据使用 {@link CredentialCipher} 加密后保存，明文只在本次请求内使用，
 * 不进入日志、审计与响应。参数非法返回 {@code body.code=400}，被引用或仍是默认返回 409。</p>
 */
@Slf4j
@Service
public class FileStorageConfigServiceImpl implements FileStorageConfigService {

    /**
     * 逻辑删除标记：正常。
     */
    private static final int NOT_DELETED_FLAG = 0;

    /**
     * 逻辑删除标记：已删除。
     */
    private static final int DELETED_FLAG = 1;

    /**
     * 版本号起始值。
     */
    private static final int INITIAL_VERSION = 1;

    /**
     * path-style 开启时数据库列的取值。
     */
    private static final int PATH_STYLE_ENABLED = 1;

    /**
     * path-style 关闭时数据库列的取值。
     */
    private static final int PATH_STYLE_DISABLED = 0;

    /**
     * 存储配置数据访问。
     */
    private final FileStorageConfigMapper configMapper;

    /**
     * 默认指针数据访问。
     */
    private final FileStorageDefaultMapper defaultMapper;

    /**
     * 文件记录数据访问，只用于引用检查。
     */
    private final FileRecordMapper recordMapper;

    /**
     * 凭据加解密端口。
     */
    private final CredentialCipher credentialCipher;

    /**
     * 存储适配工厂，配置变化后释放旧适配实例。
     */
    private final StorageProviderFactory providerFactory;

    /**
     * 默认存储方案解析组件。
     */
    private final StorageConfigResolver configResolver;

    /**
     * 文件与存储配置，提供上传硬上限。
     */
    private final ForgeFileProperties properties;

    /**
     * 存储配置转换器。
     */
    private final StorageConfigConverter converter;

    /**
     * 构造文件存储配置用例实现。
     *
     * @param configMapper     存储配置数据访问
     * @param defaultMapper    默认指针数据访问
     * @param recordMapper     文件记录数据访问
     * @param credentialCipher 凭据加解密端口
     * @param providerFactory  存储适配工厂
     * @param configResolver   默认存储方案解析组件
     * @param properties       文件与存储配置
     * @param converter        存储配置转换器
     */
    public FileStorageConfigServiceImpl(FileStorageConfigMapper configMapper, FileStorageDefaultMapper defaultMapper,
            FileRecordMapper recordMapper, CredentialCipher credentialCipher,
            StorageProviderFactory providerFactory, StorageConfigResolver configResolver,
            ForgeFileProperties properties, StorageConfigConverter converter) {
        this.configMapper = configMapper;
        this.defaultMapper = defaultMapper;
        this.recordMapper = recordMapper;
        this.credentialCipher = credentialCipher;
        this.providerFactory = providerFactory;
        this.configResolver = configResolver;
        this.properties = properties;
        this.converter = converter;
    }

    /**
     * 查询全部存储方案与版本。
     *
     * @return 按方案代码升序、版本倒序排列的配置列表，并标记当前默认方案
     */
    @Override
    public List<StorageConfigResponse> listConfigs() {
        Long defaultConfigId = configResolver.resolveDefaultId();
        List<FileStorageConfigEntity> configs = configMapper.selectList(Wrappers
                .<FileStorageConfigEntity>lambdaQuery()
                .orderByAsc(FileStorageConfigEntity::getCode)
                .orderByDesc(FileStorageConfigEntity::getVersion));
        return configs.stream()
                .map(config -> converter.toResponse(config, isSameId(config.getId(), defaultConfigId)))
                .toList();
    }

    /**
     * 查询当前默认存储方案。
     *
     * @return 当前默认配置
     */
    @Override
    public StorageConfigResponse getDefaultConfig() {
        return converter.toResponse(configResolver.requireDefault(), true);
    }

    /**
     * 创建存储方案的新版本。
     *
     * @param request    新增入参
     * @param operatorId 操作管理员 ID
     * @return 新版本配置
     */
    @Override
    @Transactional
    public StorageConfigResponse createConfig(StorageConfigCreateRequest request, Long operatorId) {
        String code = resolveCode(request.code());
        String provider = resolveProvider(request.provider());
        StorageTarget target = resolveTarget(provider, request.baseDir(), request.endpoint(), request.region(),
                request.bucket(), request.pathStyle(), request.accessKey(), request.secretKey(), null);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        FileStorageConfigEntity entity = new FileStorageConfigEntity();
        entity.setCode(code);
        entity.setVersion(nextVersion(code));
        entity.setName(resolveName(request.name()));
        applyTarget(entity, target);
        entity.setMaxFileSize(resolveMaxFileSize(request.maxFileSize()));
        entity.setAllowedExtensions(resolveAllowedExtensions(request.allowedExtensions()));
        entity.setDeleted(NOT_DELETED_FLAG);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        configMapper.insert(entity);
        return converter.toResponse(entity, false);
    }

    /**
     * 修改尚未被文件引用的存储配置版本。
     *
     * @param id         存储配置版本 ID
     * @param request    修改入参
     * @param operatorId 操作管理员 ID
     * @return 修改后的配置
     */
    @Override
    @Transactional
    public StorageConfigResponse updateConfig(Long id, StorageConfigUpdateRequest request, Long operatorId) {
        FileStorageConfigEntity existing = requireConfig(id);
        rejectReferenced(existing.getId(), "不能原地修改，请创建新版本");
        StorageTarget target = resolveTarget(resolveProvider(request.provider()), request.baseDir(),
                request.endpoint(), request.region(), request.bucket(), request.pathStyle(), request.accessKey(),
                request.secretKey(), StorageTarget.from(existing));
        String name = resolveName(request.name());
        Long maxFileSize = resolveMaxFileSize(request.maxFileSize());
        String allowedExtensions = resolveAllowedExtensions(request.allowedExtensions());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        int updated = configMapper.update(null, Wrappers.<FileStorageConfigEntity>lambdaUpdate()
                .eq(FileStorageConfigEntity::getId, existing.getId())
                .set(FileStorageConfigEntity::getName, name)
                .set(FileStorageConfigEntity::getProvider, target.provider())
                .set(FileStorageConfigEntity::getBaseDir, target.baseDir())
                .set(FileStorageConfigEntity::getEndpoint, target.endpoint())
                .set(FileStorageConfigEntity::getRegion, target.region())
                .set(FileStorageConfigEntity::getBucket, target.bucket())
                .set(FileStorageConfigEntity::getPathStyle, toPathStyleFlag(target.pathStyle()))
                .set(FileStorageConfigEntity::getAccessKey, target.accessKey())
                .set(FileStorageConfigEntity::getSecretKey, target.secretKey())
                .set(FileStorageConfigEntity::getMaxFileSize, maxFileSize)
                .set(FileStorageConfigEntity::getAllowedExtensions, allowedExtensions)
                .set(FileStorageConfigEntity::getUpdatedAt, now)
                .set(FileStorageConfigEntity::getUpdatedBy, operatorId));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "存储配置不存在或已删除");
        }
        providerFactory.evict(existing.getId());
        return converter.toResponse(requireConfig(existing.getId()), configResolver.isDefault(existing.getId()));
    }

    /**
     * 逻辑删除尚未被引用且不是默认方案的存储配置版本。
     *
     * @param id         存储配置版本 ID
     * @param operatorId 操作管理员 ID
     */
    @Override
    @Transactional
    public void deleteConfig(Long id, Long operatorId) {
        FileStorageConfigEntity existing = requireConfig(id);
        rejectReferenced(existing.getId(), "不能删除");
        if (configResolver.isDefault(existing.getId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该存储配置当前是默认方案，请先切换默认方案再删除");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int updated = configMapper.update(null, Wrappers.<FileStorageConfigEntity>lambdaUpdate()
                .eq(FileStorageConfigEntity::getId, existing.getId())
                .set(FileStorageConfigEntity::getDeleted, DELETED_FLAG)
                .set(FileStorageConfigEntity::getUpdatedAt, now)
                .set(FileStorageConfigEntity::getUpdatedBy, operatorId));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "存储配置不存在或已删除");
        }
        providerFactory.evict(existing.getId());
    }

    /**
     * 检测存储目标是否可用。
     *
     * @param id 存储配置版本 ID
     * @return 检测耗时与结果
     */
    @Override
    public StorageTestResponse testConnection(Long id) {
        FileStorageConfigEntity config = requireConfig(id);
        long startedAt = System.nanoTime();
        try {
            StorageProvider provider = providerFactory.create(config);
            provider.verify();
            return converter.toTestResponse(config, elapsedMillis(startedAt), true, "存储目标连接正常");
        } catch (BusinessException failure) {
            log.warn("存储目标连接检测失败，configId={}，code={}", id, failure.getCode());
            return converter.toTestResponse(config, elapsedMillis(startedAt), false, failure.getMessage());
        }
    }

    /**
     * 切换默认存储方案。
     *
     * @param id         存储配置版本 ID
     * @param operatorId 操作管理员 ID
     */
    @Override
    @Transactional
    public void switchDefault(Long id, Long operatorId) {
        FileStorageConfigEntity existing = requireConfig(id);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        FileStorageDefaultEntity pointer = defaultMapper.selectById(StorageConfigResolver.DEFAULT_POINTER_ID);
        int affected;
        if (pointer == null) {
            affected = defaultMapper.insertPointer(StorageConfigResolver.DEFAULT_POINTER_ID, existing.getId(), now,
                    operatorId);
        } else {
            affected = defaultMapper.updatePointer(StorageConfigResolver.DEFAULT_POINTER_ID, existing.getId(), now,
                    operatorId);
        }
        if (affected == 0) {
            log.error("默认存储指针更新失败，configId={}", id);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "默认存储方案切换失败，请稍后重试");
        }
    }

    /**
     * 查询存储配置版本，不存在时按资源不存在处理。
     *
     * @param id 存储配置版本 ID
     * @return 存储配置实体
     * @throws BusinessException 配置不存在或已删除时抛出 404
     */
    private FileStorageConfigEntity requireConfig(Long id) {
        FileStorageConfigEntity config = id == null ? null : configMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "存储配置不存在或已删除");
        }
        return config;
    }

    /**
     * 拒绝操作已被文件记录引用的配置版本。
     *
     * <p>引用计数包含已逻辑删除的文件记录：记录行仍然存在并引用该版本，
     * 数据库外键也阻止物理删除，因此不能只统计未删除记录。</p>
     *
     * @param configId      存储配置版本 ID
     * @param failureSuffix 冲突提示的后半句，说明当前操作被拒绝的原因
     * @throws BusinessException 存在引用时抛出 409
     */
    private void rejectReferenced(Long configId, String failureSuffix) {
        long references = recordMapper.countByStorageConfigId(configId);
        if (references > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该存储配置版本已被 " + references + " 个文件引用，" + failureSuffix);
        }
    }

    /**
     * 规范化并校验存储方案代码。
     *
     * @param rawCode 原始方案代码
     * @return 小写方案代码
     * @throws BusinessException 格式不合法时抛出 400
     */
    private String resolveCode(String rawCode) {
        String code = FileStoragePolicy.normalizeCode(rawCode);
        if (!FileStoragePolicy.isValidCode(code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "存储方案代码必须以字母开头，只能包含字母、数字、下划线与连字符，长度不超过 64");
        }
        return code;
    }

    /**
     * 计算同代码下的下一个版本号。
     *
     * @param code 已规范化的方案代码
     * @return 下一个版本号
     */
    private int nextVersion(String code) {
        Integer maxVersion = configMapper.selectMaxVersionByCode(code);
        return maxVersion == null ? INITIAL_VERSION : maxVersion + 1;
    }

    /**
     * 规范化并校验存储方案名称。
     *
     * @param rawName 原始名称
     * @return 名称
     * @throws BusinessException 名称为空时抛出 400
     */
    private String resolveName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存储方案名称不能为空");
        }
        return rawName.strip();
    }

    /**
     * 规范化并校验存储类型。
     *
     * @param rawProvider 原始存储类型
     * @return 小写存储类型
     * @throws BusinessException 类型不受支持时抛出 400
     */
    private String resolveProvider(String rawProvider) {
        String provider = FileStoragePolicy.normalizeProvider(rawProvider);
        if (!FileStoragePolicy.isSupportedProvider(provider)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存储类型只能是 local 或 s3");
        }
        return provider;
    }

    /**
     * 校验单文件大小上限。
     *
     * @param requested 请求的上限（字节）
     * @return 校验通过的上限
     * @throws BusinessException 小于 1 或高于应用硬上限时抛出 400
     */
    private long resolveMaxFileSize(Long requested) {
        if (requested == null || requested < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单文件大小上限必须大于 0");
        }
        long hardMax = hardMaxFileSize();
        if (requested > hardMax) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "单文件大小上限不能高于应用上传硬上限（" + hardMax + " 字节）");
        }
        return requested;
    }

    /**
     * 读取应用上传硬上限。
     *
     * @return 硬上限字节数，未配置时返回 {@link Long#MAX_VALUE}
     */
    private long hardMaxFileSize() {
        return properties.getHardMaxFileSize() == null ? Long.MAX_VALUE : properties.getHardMaxFileSize().toBytes();
    }

    /**
     * 规范化并校验允许的扩展名。
     *
     * @param rawExtensions 原始配置，逗号分隔
     * @return 保存用的逗号分隔字符串，未配置时返回 {@code null}
     * @throws BusinessException 数量或格式不合法时抛出 400
     */
    private String resolveAllowedExtensions(String rawExtensions) {
        List<String> extensions = FileStoragePolicy.splitExtensions(rawExtensions);
        if (extensions.size() > FileStoragePolicy.EXTENSION_COUNT_MAX) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "允许的扩展名数量不能超过 " + FileStoragePolicy.EXTENSION_COUNT_MAX);
        }
        for (String extension : extensions) {
            if (!FileStoragePolicy.isValidExtension(extension)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "允许的扩展名只能是 1 到 16 位字母或数字");
            }
        }
        return FileStoragePolicy.joinExtensions(extensions);
    }

    /**
     * 解析存储目标字段。
     *
     * <p>按存储类型分别校验必填项：本地存储必须有位于根目录内的相对目录，
     * 对象存储必须有访问地址、桶名称与凭据；不相关的字段一律清空，避免历史值残留造成误解。
     * 修改时凭据留空表示沿用原密文。</p>
     *
     * @param provider   已规范化的存储类型
     * @param baseDir    相对目录
     * @param endpoint   访问地址
     * @param region     区域
     * @param bucket     桶名称
     * @param pathStyle  path-style 开关，允许为 {@code null}
     * @param accessKey  访问凭据明文，允许为空
     * @param secretKey  访问密钥明文，允许为空
     * @param previous   修改前的目标值，新增时为 {@code null}
     * @return 规范化后的目标字段
     * @throws BusinessException 必填项缺失或格式不合法时抛出 400
     */
    private StorageTarget resolveTarget(String provider, String baseDir, String endpoint, String region, String bucket,
            Boolean pathStyle, String accessKey, String secretKey, StorageTarget previous) {
        boolean pathStyleEnabled = pathStyle == null || pathStyle;
        if (FileStoragePolicy.PROVIDER_LOCAL.equals(provider)) {
            String directory = FileStoragePolicy.normalizeRelativeDirectory(baseDir);
            if (directory == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "本地存储方案的相对目录必填，且必须是不含上级目录的相对路径");
            }
            return new StorageTarget(provider, directory, null, null, null, pathStyleEnabled, null, null);
        }
        String normalizedEndpoint = requireText(endpoint, "对象存储访问地址必填");
        if (!FileStoragePolicy.isValidEndpoint(normalizedEndpoint)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "对象存储访问地址必须是以 http 或 https 开头的完整地址");
        }
        String normalizedBucket = requireText(bucket, "对象存储桶名称必填");
        String accessKeyCipher = resolveCredentialCipher(accessKey, previousFor(previous, true), "对象存储访问凭据必填");
        String secretKeyCipher = resolveCredentialCipher(secretKey, previousFor(previous, false), "对象存储访问密钥必填");
        return new StorageTarget(provider, null, normalizedEndpoint, trimToNull(region), normalizedBucket,
                pathStyleEnabled, accessKeyCipher, secretKeyCipher);
    }

    /**
     * 解析凭据密文。
     *
     * @param provided           本次提交的明文凭据，允许为空表示保持原凭据
     * @param previousCipherText 原密文，允许为 {@code null}
     * @param missingMessage     既未提交明文又没有原密文时的中文提示
     * @return 加密后的密文
     * @throws BusinessException 缺少凭据时抛出 400，主密钥缺失或不可用时由凭据端口抛出
     */
    private String resolveCredentialCipher(String provided, String previousCipherText, String missingMessage) {
        if (provided != null && !provided.isBlank()) {
            return credentialCipher.encrypt(provided.strip());
        }
        if (previousCipherText != null && !previousCipherText.isBlank()) {
            return previousCipherText;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, missingMessage);
    }

    /**
     * 读取修改前的凭据密文。
     *
     * @param previous   修改前的目标值，允许为 {@code null}
     * @param accessKey  {@code true} 取访问凭据，{@code false} 取访问密钥
     * @return 原密文，没有原值时返回 {@code null}
     */
    private String previousFor(StorageTarget previous, boolean accessKey) {
        if (previous == null) {
            return null;
        }
        return accessKey ? previous.accessKey() : previous.secretKey();
    }

    /**
     * 把目标字段写入实体。
     *
     * @param entity 存储配置实体
     * @param target 目标字段
     */
    private void applyTarget(FileStorageConfigEntity entity, StorageTarget target) {
        entity.setProvider(target.provider());
        entity.setBaseDir(target.baseDir());
        entity.setEndpoint(target.endpoint());
        entity.setRegion(target.region());
        entity.setBucket(target.bucket());
        entity.setPathStyle(toPathStyleFlag(target.pathStyle()));
        entity.setAccessKey(target.accessKey());
        entity.setSecretKey(target.secretKey());
    }

    /**
     * 把布尔开关转换为数据库列取值。
     *
     * @param pathStyle 是否启用 path-style
     * @return 启用返回 1，关闭返回 0
     */
    private int toPathStyleFlag(boolean pathStyle) {
        return pathStyle ? PATH_STYLE_ENABLED : PATH_STYLE_DISABLED;
    }

    /**
     * 校验必填文本。
     *
     * @param value   文本值
     * @param message 缺失时的中文提示
     * @return 去空格后的文本
     * @throws BusinessException 文本为空时抛出 400
     */
    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        return value.strip();
    }

    /**
     * 去空格并把空文本转换为 {@code null}。
     *
     * @param value 文本值，允许为 {@code null}
     * @return 去空格后的文本，空文本返回 {@code null}
     */
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    /**
     * 判断两个 ID 是否相同。
     *
     * @param left  左值，允许为 {@code null}
     * @param right 右值，允许为 {@code null}
     * @return 均非空且相等时返回 {@code true}
     */
    private boolean isSameId(Long left, Long right) {
        return left != null && left.equals(right);
    }

    /**
     * 计算自起始时刻以来的耗时。
     *
     * @param startedAt 起始时刻，取值来自 {@link System#nanoTime()}
     * @return 耗时毫秒数
     */
    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    /**
     * 规范化后的存储目标字段。
     *
     * <p>只在本实现内部传递：把“按类型校验并清空无关字段”的结果一次性交给写库逻辑，
     * 避免每个字段各写一遍分支。凭据字段保存的是密文。</p>
     *
     * @param provider  存储类型
     * @param baseDir   本地存储相对目录，对象存储时为空
     * @param endpoint  对象存储访问地址，本地存储时为空
     * @param region    对象存储区域，可为空
     * @param bucket    对象存储桶名称，本地存储时为空
     * @param pathStyle 是否启用 path-style
     * @param accessKey 访问凭据密文，本地存储时为空
     * @param secretKey 访问密钥密文，本地存储时为空
     */
    private record StorageTarget(String provider, String baseDir, String endpoint, String region, String bucket,
            boolean pathStyle, String accessKey, String secretKey) {

        /**
         * 从已有配置读取目标字段。
         *
         * @param entity 存储配置实体
         * @return 目标字段，凭据为已保存的密文
         */
        private static StorageTarget from(FileStorageConfigEntity entity) {
            return new StorageTarget(entity.getProvider(), entity.getBaseDir(), entity.getEndpoint(),
                    entity.getRegion(), entity.getBucket(),
                    entity.getPathStyle() != null && entity.getPathStyle() == PATH_STYLE_ENABLED,
                    entity.getAccessKey(), entity.getSecretKey());
        }
    }
}
