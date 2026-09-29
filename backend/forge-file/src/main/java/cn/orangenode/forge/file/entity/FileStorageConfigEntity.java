package cn.orangenode.forge.file.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 文件存储配置实体。
 *
 * <p>对应表 {@code file_storage_config}。存储目标按 {@code (code, version)} 版本化：
 * 同一个方案代码下，目标（本地相对目录、访问地址、桶名称）变化时新增版本而不是原地修改，
 * 文件记录引用上传时固定的版本 ID，因此切换默认方案不影响历史文件的读取。</p>
 *
 * <p>{@code accessKey}、{@code secretKey} 保存的是 AES-GCM 密文，主密钥只在环境中；
 * 两个字段不允许出现在任何响应、日志与异常信息里，接口只回显“是否已配置凭据”。</p>
 */
@Getter
@Setter
@TableName("file_storage_config")
public class FileStorageConfigEntity {

    /**
     * 主键，数据库自增。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 存储方案代码，同代码下版本递增。
     */
    private String code;

    /**
     * 版本号，从 1 开始；目标变化时创建新版本。
     */
    private Integer version;

    /**
     * 方案名称。
     */
    private String name;

    /**
     * 存储类型：{@code local} 本地文件系统，{@code s3} 兼容对象存储。
     */
    private String provider;

    /**
     * 本地存储相对目录（{@code provider=local}），必须位于环境根目录之内。
     */
    private String baseDir;

    /**
     * 对象存储访问地址（{@code provider=s3}）。
     */
    private String endpoint;

    /**
     * 对象存储区域（{@code provider=s3}），可为空。
     */
    private String region;

    /**
     * 对象存储桶名称（{@code provider=s3}）。
     */
    private String bucket;

    /**
     * 是否使用 path-style 访问，MinIO 等兼容服务需要，{@code 1} 为启用。
     */
    private Integer pathStyle;

    /**
     * 访问凭据密文（AES-GCM，{@code provider=s3}），明文不进入响应与日志。
     */
    private String accessKey;

    /**
     * 访问密钥密文（AES-GCM，{@code provider=s3}），明文不进入响应与日志。
     */
    private String secretKey;

    /**
     * 单文件大小上限（字节），不得高于应用硬上限。
     */
    private Long maxFileSize;

    /**
     * 允许的扩展名，逗号分隔且不含点；为空表示不限制扩展名。
     */
    private String allowedExtensions;

    /**
     * 逻辑删除标记，0 正常、1 已删除。
     */
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间（UTC）。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间（UTC）。
     */
    private LocalDateTime updatedAt;

    /**
     * 创建者管理员 ID。
     */
    private Long createdBy;

    /**
     * 更新者管理员 ID。
     */
    private Long updatedBy;
}
