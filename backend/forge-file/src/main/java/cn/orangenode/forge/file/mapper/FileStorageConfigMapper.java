package cn.orangenode.forge.file.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import cn.orangenode.forge.file.entity.FileStorageConfigEntity;

/**
 * 文件存储配置数据访问。
 *
 * <p>只由文件模块内部使用，其他模块通过文件公开服务使用存储能力，不直接注入本 Mapper。</p>
 *
 * <p>版本号查询使用显式 SQL 且不过滤逻辑删除：唯一键 {@code (code, version)} 不含 {@code deleted}，
 * 已删除版本仍然占用版本号，因此新版本必须在此基础上递增，否则会撞唯一键。</p>
 */
@Mapper
public interface FileStorageConfigMapper extends BaseMapper<FileStorageConfigEntity> {

    /**
     * 查询指定方案代码已使用的最大版本号。
     *
     * @param code 已规范化的方案代码
     * @return 最大版本号，该代码尚无任何版本时返回 {@code null}
     */
    @Select("select max(version) from file_storage_config where code = #{code}")
    Integer selectMaxVersionByCode(@Param("code") String code);

    /**
     * 查询指定方案代码下最新的有效版本。
     *
     * <p>用于默认指针尚未建立时的兜底选择：按 {@code forge.file.default-config-code} 取最新版本，
     * 使空库安装后创建的第一个方案可以直接用于上传。</p>
     *
     * @param code 已规范化的方案代码
     * @return 最新版本实体，没有有效版本时返回 {@code null}
     */
    @Select("select id, code, version, name, provider, base_dir, endpoint, region, bucket, path_style, "
            + "access_key, secret_key, max_file_size, allowed_extensions, deleted, created_at, updated_at, "
            + "created_by, updated_by from file_storage_config "
            + "where code = #{code} and deleted = 0 order by version desc limit 1")
    FileStorageConfigEntity selectLatestByCode(@Param("code") String code);
}
