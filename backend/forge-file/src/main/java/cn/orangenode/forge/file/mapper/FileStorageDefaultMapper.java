package cn.orangenode.forge.file.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import cn.orangenode.forge.file.entity.FileStorageDefaultEntity;

/**
 * 默认存储配置指针数据访问。
 *
 * <p>只由文件模块内部使用。指针表没有逻辑删除，写入使用显式 SQL：主键不是自增列，
 * 必须显式传入固定 ID，因此不依赖通用插入的生成键行为。</p>
 */
@Mapper
public interface FileStorageDefaultMapper extends BaseMapper<FileStorageDefaultEntity> {

    /**
     * 建立默认指针行。
     *
     * @param id        固定主键
     * @param configId  默认存储配置 ID
     * @param updatedAt 更新时间（UTC）
     * @param updatedBy 更新者管理员 ID
     * @return 受影响行数
     */
    @Insert("insert into file_storage_default (id, config_id, updated_at, updated_by) "
            + "values (#{id}, #{configId}, #{updatedAt}, #{updatedBy})")
    int insertPointer(@Param("id") Long id, @Param("configId") Long configId,
            @Param("updatedAt") LocalDateTime updatedAt, @Param("updatedBy") Long updatedBy);

    /**
     * 更新默认指针指向的配置版本。
     *
     * @param id        固定主键
     * @param configId  默认存储配置 ID
     * @param updatedAt 更新时间（UTC）
     * @param updatedBy 更新者管理员 ID
     * @return 受影响行数，指针行不存在时返回 0
     */
    @Update("update file_storage_default set config_id = #{configId}, updated_at = #{updatedAt}, "
            + "updated_by = #{updatedBy} where id = #{id}")
    int updatePointer(@Param("id") Long id, @Param("configId") Long configId,
            @Param("updatedAt") LocalDateTime updatedAt, @Param("updatedBy") Long updatedBy);
}
