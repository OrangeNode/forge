package cn.orangenode.forge.file.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.file.converter.FileRecordConverter;
import cn.orangenode.forge.file.entity.FileRecordEntity;
import cn.orangenode.forge.file.entity.FileStorageConfigEntity;
import cn.orangenode.forge.file.mapper.FileRecordMapper;
import cn.orangenode.forge.file.mapper.FileStorageConfigMapper;
import cn.orangenode.forge.file.policy.FileStoragePolicy;
import cn.orangenode.forge.file.request.FileRecordPageRequest;
import cn.orangenode.forge.file.response.FileRecordResponse;
import cn.orangenode.forge.file.response.FileUploadResponse;
import cn.orangenode.forge.file.service.FileService;
import cn.orangenode.forge.file.service.StorageConfigResolver;
import cn.orangenode.forge.file.storage.ObjectKeyGenerator;
import cn.orangenode.forge.file.storage.StorageProviderFactory;
import cn.orangenode.forge.file.support.FileIds;
import cn.orangenode.forge.framework.config.ForgeFileProperties;
import cn.orangenode.forge.framework.page.PageResponses;
import cn.orangenode.forge.framework.storage.StorageProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件用例实现。
 *
 * <p>上传顺序是“校验 → 写对象 → 写元数据”：上传开始时固定使用的存储配置版本，
 * 管理员中途切换默认方案不会改变本次目标；元数据写入失败时删除刚写入的对象并记录带对象键的清理日志，
 * 然后返回失败，绝不留下“成功”结果。对象写入失败本身不会产生任何元数据。</p>
 *
 * <p>下载按文件记录里固定的配置版本读取，因此历史文件不受默认方案切换影响。
 * 删除顺序是“先清理对象再标记记录”：对象清理失败时返回 {@code body.code=503} 且记录保持不变，
 * 调用方重试即可完成（对象不存在视为删除成功，重试是幂等的）。
 * 删除不声明事务：单条更新自身具备原子性，先清理对象可以避免在远端调用期间占用数据库连接。</p>
 *
 * <p>校验规则：扩展名白名单为空表示不限制，大小上限取方案配置与应用硬上限的较小值，
 * 文件名只保留名称部分并拒绝控制字符，内容类型只接受可打印 ASCII。</p>
 */
@Slf4j
@Service
public class FileServiceImpl implements FileService {

    /**
     * 逻辑删除标记：正常。
     */
    private static final int NOT_DELETED_FLAG = 0;

    /**
     * 逻辑删除标记：已删除。
     */
    private static final int DELETED_FLAG = 1;

    /**
     * 文件记录数据访问。
     */
    private final FileRecordMapper recordMapper;

    /**
     * 存储配置数据访问，用于按记录的配置版本定位存储目标。
     */
    private final FileStorageConfigMapper configMapper;

    /**
     * 存储适配工厂。
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
     * 文件记录转换器。
     */
    private final FileRecordConverter converter;

    /**
     * 构造文件用例实现。
     *
     * @param recordMapper    文件记录数据访问
     * @param configMapper    存储配置数据访问
     * @param providerFactory 存储适配工厂
     * @param configResolver  默认存储方案解析组件
     * @param properties      文件与存储配置
     * @param converter       文件记录转换器
     */
    public FileServiceImpl(FileRecordMapper recordMapper, FileStorageConfigMapper configMapper,
            StorageProviderFactory providerFactory, StorageConfigResolver configResolver,
            ForgeFileProperties properties, FileRecordConverter converter) {
        this.recordMapper = recordMapper;
        this.configMapper = configMapper;
        this.providerFactory = providerFactory;
        this.configResolver = configResolver;
        this.properties = properties;
        this.converter = converter;
    }

    /**
     * 上传文件到当前默认存储方案。
     *
     * @param file       上传的 multipart 文件
     * @param uploaderId 上传者管理员 ID
     * @return 文件 ID 与安全元数据
     */
    @Override
    public FileUploadResponse upload(MultipartFile file, Long uploaderId, String uploaderName) {
        if (uploaderId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        FileStorageConfigEntity config = configResolver.requireDefault();
        String originalName = requireOriginalName(file.getOriginalFilename());
        String contentType = resolveContentType(file.getContentType());
        long size = file.getSize();
        assertSizeAllowed(size, config);
        String extension = FileStoragePolicy.resolveExtension(originalName);
        assertExtensionAllowed(config.getAllowedExtensions(), extension);

        StorageProvider provider = providerFactory.create(config);
        String objectKey = ObjectKeyGenerator.generate(extension);
        try (InputStream content = file.getInputStream()) {
            provider.store(objectKey, content, size, contentType);
        } catch (IOException failure) {
            log.error("上传文件内容读取失败，storageConfigId={}", config.getId());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "上传文件读取失败，请重试", failure);
        }

        FileRecordEntity record = buildRecord(config, objectKey, originalName, contentType, size, uploaderId,
                uploaderName);
        try {
            recordMapper.insert(record);
        } catch (RuntimeException failure) {
            compensateStoredObject(provider, objectKey, config.getId());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败，请稍后重试", failure);
        }
        return converter.toUploadResponse(record);
    }

    /**
     * 按文件 ID 查询元数据。
     *
     * @param id 文件 ID
     * @return 文件元数据
     */
    @Override
    public FileRecordResponse getRecord(Long id) {
        return converter.toResponse(requireRecord(id));
    }

    /**
     * 分页查询文件元数据。
     *
     * @param request 分页与筛选入参
     * @return 文件元数据分页结果，按创建时间倒序
     */
    @Override
    public PageResponse<FileRecordResponse> pageRecords(FileRecordPageRequest request) {
        LambdaQueryWrapper<FileRecordEntity> condition = new LambdaQueryWrapper<>();
        condition.like(hasText(request.getOriginalName()), FileRecordEntity::getOriginalName,
                request.getOriginalName());
        Long storageConfigId = FileIds.toOptionalLong(request.getStorageConfigId(), "存储配置 ID");
        condition.eq(storageConfigId != null, FileRecordEntity::getStorageConfigId, storageConfigId);
        condition.orderByDesc(FileRecordEntity::getCreatedAt);
        condition.orderByDesc(FileRecordEntity::getId);
        Page<FileRecordEntity> page = new Page<>(request.getPageNum(), request.getPageSize());
        return PageResponses.from(recordMapper.selectPage(page, condition), converter::toResponse);
    }

    /**
     * 按文件 ID 打开内容流。
     *
     * @param id 文件 ID
     * @return 文件内容流，由调用方负责关闭
     */
    @Override
    public InputStream openContent(Long id) {
        FileRecordEntity record = requireRecord(id);
        FileStorageConfigEntity config = requireRecordConfig(record);
        return providerFactory.create(config).read(record.getObjectKey());
    }

    /**
     * 逻辑删除文件记录并清理对象。
     *
     * @param id         文件 ID
     * @param operatorId 操作管理员 ID
     */
    @Override
    public void deleteRecord(Long id, Long operatorId) {
        FileRecordEntity record = requireRecord(id);
        FileStorageConfigEntity config = requireRecordConfig(record);
        providerFactory.create(config).delete(record.getObjectKey());

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int updated = recordMapper.update(null, Wrappers.<FileRecordEntity>lambdaUpdate()
                .eq(FileRecordEntity::getId, record.getId())
                .set(FileRecordEntity::getDeleted, DELETED_FLAG)
                .set(FileRecordEntity::getUpdatedAt, now)
                .set(FileRecordEntity::getUpdatedBy, operatorId));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在或已删除");
        }
    }

    /**
     * 查询文件记录，不存在或已删除时按资源不存在处理。
     *
     * @param id 文件 ID
     * @return 文件记录实体
     * @throws BusinessException 文件不存在或已删除时抛出 404
     */
    private FileRecordEntity requireRecord(Long id) {
        FileRecordEntity record = id == null ? null : recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在或已删除");
        }
        return record;
    }

    /**
     * 查询文件记录引用的存储配置版本。
     *
     * <p>配置文件被引用时不允许删除，因此这里查不到只可能是数据被人工改动，
     * 属于服务端一致性问题：对外给通用说明，日志记录文件 ID 便于排查。</p>
     *
     * @param record 文件记录实体
     * @return 存储配置实体
     * @throws BusinessException 引用的配置不可用时抛出 500
     */
    private FileStorageConfigEntity requireRecordConfig(FileRecordEntity record) {
        FileStorageConfigEntity config = configMapper.selectById(record.getStorageConfigId());
        if (config == null) {
            log.error("文件引用的存储配置版本不可用，fileId={}", record.getId());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件内容暂时不可用，请联系管理员");
        }
        return config;
    }

    /**
     * 组装文件记录实体。
     *
     * @param config       本次上传使用的存储配置版本
     * @param objectKey    随机对象键
     * @param originalName 清洗后的原始文件名
     * @param contentType  内容类型，可为空
     * @param size         文件大小（字节）
     * @param uploaderId   上传者管理员 ID
     * @param uploaderName 上传者名称快照，可为空
     * @return 待写入的文件记录实体
     */
    private FileRecordEntity buildRecord(FileStorageConfigEntity config, String objectKey, String originalName,
            String contentType, long size, Long uploaderId, String uploaderName) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        FileRecordEntity record = new FileRecordEntity();
        record.setStorageConfigId(config.getId());
        record.setObjectKey(objectKey);
        record.setOriginalName(originalName);
        record.setContentType(contentType);
        record.setSizeBytes(size);
        record.setUploaderId(uploaderId);
        record.setUploaderName(uploaderName);
        record.setDeleted(NOT_DELETED_FLAG);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setCreatedBy(uploaderId);
        record.setUpdatedBy(uploaderId);
        return record;
    }

    /**
     * 补偿删除刚写入的对象。
     *
     * <p>元数据写入失败后调用。补偿成功记录警告日志，失败记录错误日志：
     * 两条日志都带存储配置版本与对象键，便于运维按对象键在存储端定位并清理。
     * 补偿失败不改变对外结果，调用方仍收到上传失败。</p>
     *
     * @param provider        写入对象时使用的存储适配
     * @param objectKey       对象键
     * @param storageConfigId 存储配置版本 ID
     */
    private void compensateStoredObject(StorageProvider provider, String objectKey, Long storageConfigId) {
        try {
            provider.delete(objectKey);
            log.warn("文件元数据写入失败，已删除刚写入的对象，storageConfigId={}，objectKey={}", storageConfigId,
                    objectKey);
        } catch (RuntimeException failure) {
            log.error("文件元数据写入失败且对象清理失败，需要人工清理，storageConfigId={}，objectKey={}",
                    storageConfigId, objectKey);
        }
    }

    /**
     * 校验并清洗原始文件名。
     *
     * @param rawName 原始文件名
     * @return 只含名称部分的文件名
     * @throws BusinessException 文件名为空、含控制字符或超过列长度时抛出 400
     */
    private String requireOriginalName(String rawName) {
        String originalName = FileStoragePolicy.sanitizeOriginalName(rawName);
        if (originalName == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名不能为空或包含非法字符");
        }
        if (originalName.length() > FileStoragePolicy.ORIGINAL_NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "文件名长度不能超过 " + FileStoragePolicy.ORIGINAL_NAME_MAX_LENGTH + " 个字符");
        }
        return originalName;
    }

    /**
     * 校验并规范化内容类型。
     *
     * <p>内容类型来自客户端，会写入元数据并用于下载响应头，因此只接受可打印 ASCII 字符。</p>
     *
     * @param rawContentType 上传声明的内容类型，可以为空
     * @return 规范化后的内容类型，未声明时返回 {@code null}
     * @throws BusinessException 长度或字符集不合法时抛出 400
     */
    private String resolveContentType(String rawContentType) {
        if (rawContentType == null || rawContentType.isBlank()) {
            return null;
        }
        String contentType = rawContentType.strip();
        if (!FileStoragePolicy.isValidContentType(contentType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件内容类型信息不正确");
        }
        return contentType;
    }

    /**
     * 校验文件大小。
     *
     * <p>上限取方案配置与应用硬上限的较小值：应用硬上限由容器与配置共同保证，
     * 方案配置由管理员设置，两者都不允许被绕过。</p>
     *
     * @param size   文件大小（字节）
     * @param config 本次上传使用的存储配置版本
     * @throws BusinessException 超过上限时抛出 413
     */
    private void assertSizeAllowed(long size, FileStorageConfigEntity config) {
        long configLimit = config.getMaxFileSize() == null ? 0L : config.getMaxFileSize();
        long hardLimit = properties.getHardMaxFileSize() == null
                ? Long.MAX_VALUE : properties.getHardMaxFileSize().toBytes();
        if (size > Math.min(configLimit, hardLimit)) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "上传文件超过当前存储方案允许的大小上限");
        }
    }

    /**
     * 校验扩展名是否在允许范围内。
     *
     * <p>白名单为空表示不限制扩展名；非空时没有扩展名的文件也会被拒绝，
     * 避免通过“不带后缀”绕过类型限制。</p>
     *
     * @param allowedExtensions 存储配置里的允许扩展名
     * @param extension         本次上传文件的小写扩展名，无扩展名时为空串
     * @throws BusinessException 扩展名不在范围内时抛出 400
     */
    private void assertExtensionAllowed(String allowedExtensions, String extension) {
        Set<String> allowed = FileStoragePolicy.parseAllowedExtensions(allowedExtensions);
        if (allowed.isEmpty()) {
            return;
        }
        if (extension.isEmpty() || !allowed.contains(extension)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件类型不在允许的扩展名范围内");
        }
    }

    /**
     * 判断筛选条件是否有效。
     *
     * @param value 筛选条件值，允许为 {@code null}
     * @return 去空格后非空时返回 {@code true}
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
