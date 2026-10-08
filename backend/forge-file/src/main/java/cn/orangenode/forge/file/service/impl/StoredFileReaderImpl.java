package cn.orangenode.forge.file.service.impl;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.file.response.FileRecordResponse;
import cn.orangenode.forge.file.service.FileService;
import cn.orangenode.forge.framework.storage.StoredFileContent;
import cn.orangenode.forge.framework.storage.StoredFileReader;

/**
 * 文件读取端口实现，复用文件用例的存在性校验与存储版本读取，不暴露对象键。
 */
@Component
public class StoredFileReaderImpl implements StoredFileReader {

    /**
     * 文件用例。
     */
    private final FileService fileService;

    /**
     * 构造文件读取端口实现。
     *
     * @param fileService 文件用例
     */
    public StoredFileReaderImpl(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 判断文件是否存在且未删除。
     *
     * @param fileId 文件 ID
     * @return 存在时为 {@code true}
     */
    @Override
    public boolean exists(Long fileId) {
        try {
            fileService.getRecord(fileId);
            return true;
        } catch (BusinessException missing) {
            return false;
        }
    }

    /**
     * 打开文件内容与媒体类型。
     *
     * @param fileId 文件 ID
     * @return 文件内容与元数据
     */
    @Override
    public StoredFileContent open(Long fileId) {
        FileRecordResponse record = fileService.getRecord(fileId);
        return new StoredFileContent(fileService.openContent(fileId), record.contentType(), record.sizeBytes());
    }

    /**
     * 确保文件已公开并返回公开下载路径。
     *
     * @param fileId 文件 ID
     * @return 公开下载路径
     */
    @Override
    public String publicPath(Long fileId) {
        return fileService.share(fileId, null).publicUrl();
    }
}
