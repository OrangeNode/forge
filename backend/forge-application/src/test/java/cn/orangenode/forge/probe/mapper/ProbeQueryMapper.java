package cn.orangenode.forge.probe.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 测试专用映射器。
 *
 * <p>与真实业务模块的 {@code <模块>.mapper} 位置一致，用于验证应用启动时的 Mapper 扫描范围：
 * 只有带 {@code @Mapper} 注解、位于扫描根包下的接口才会被注册为 Bean。</p>
 */
@Mapper
public interface ProbeQueryMapper {

    /**
     * 查询常量 1，用于确认映射器可通过主库连接真正执行 SQL。
     *
     * @return 常量 1
     */
    @Select("select 1")
    Integer selectOne();
}
