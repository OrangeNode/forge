package cn.orangenode.forge.file.service;

import java.io.InputStream;

import org.springframework.web.multipart.MultipartFile;

import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.file.request.FileRecordPageRequest;
import cn.orangenode.forge.file.response.FileRecordResponse;
import cn.orangenode.forge.file.response.FileUploadResponse;

/**
 * 文件用例，供本模块接口层与其他业务模块使用。
 *
 * <p>只负责文件元数据、调用者授权前置条件与失败处理，具体对象读写交给存储适配：
 * 上传先写对象再写元数据，元数据写入失败时补偿删除刚写入的对象，绝不返回成功；
 * 删除先清理对象再标记记录，对象清理失败时记录保持不变、可以重试。</p>
 *
 * <p>对外只暴露文件 ID、元数据与内容流，不暴露对象键、存储根目录、访问地址与凭据。
 * 调用方负责业务侧的资源授权：本服务只校验登录身份与文件是否存在。
 * 可预期失败抛业务异常，由统一错误出口转换为 {@code body.code}：缺少身份 401、
 * 文件校验失败 400、超过大小上限 413、文件或方案不存在 404、存储不可用 503。</p>
 */
public interface FileService {

    /**
     * 上传文件到当前默认存储方案。
     *
     * <p>校验扩展名白名单与大小上限，使用随机对象键写入对象，再保存文件元数据。
     * 上传过程中使用的存储配置版本在开始时固定，管理员中途切换默认方案不会改变本次上传的目标。</p>
     *
     * @param file         上传的 multipart 文件
     * @param uploaderId   上传者管理员 ID
     * @param uploaderName 上传者名称快照，可为空
     * @return 文件 ID 与安全元数据
     */
    FileUploadResponse upload(MultipartFile file, Long uploaderId, String uploaderName);

    /**
     * 按文件 ID 查询元数据，文件不存在或已删除时返回 {@code body.code=404}。
     *
     * @param id 文件 ID
     * @return 文件元数据
     */
    FileRecordResponse getRecord(Long id);

    /**
     * 分页查询文件元数据，筛选条件中的 ID 非法时返回 {@code body.code=400}。
     *
     * @param request 分页与筛选入参
     * @return 文件元数据分页结果，按创建时间倒序
     */
    PageResponse<FileRecordResponse> pageRecords(FileRecordPageRequest request);

    /**
     * 按文件 ID 打开内容流。
     *
     * <p>调用方必须先完成业务授权并负责关闭返回的流。流打开失败发生在开始输出之前，
     * 因此可以由统一错误出口返回 JSON 错误体。</p>
     *
     * @param id 文件 ID
     * @return 文件内容流，由调用方负责关闭
     */
    InputStream openContent(Long id);

    /**
     * 逻辑删除文件记录并清理对象。
     *
     * <p>先清理对象再标记记录：对象清理失败时记录保持不变并返回 {@code body.code=503}，
     * 调用方可以重试，不会出现“记录已删除但对象仍留在存储里”却对调用方报成功的情况。</p>
     *
     * @param id         文件 ID
     * @param operatorId 操作管理员 ID，允许为 {@code null} 表示系统操作
     */
    void deleteRecord(Long id, Long operatorId);
}
