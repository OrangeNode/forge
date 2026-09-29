package cn.orangenode.forge.file.storage;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 存储对象键生成器。
 *
 * <p>对象键由随机值与 UTC 日期组成，结构与原始文件名无关：中文文件名、重名文件、
 * 同名覆盖都不会影响对象键，也不会有任何调用方输入进入对象键，因此不存在路径拼接风险。
 * 目录分为日期与两段随机值，避免单个目录下对象过多。</p>
 *
 * <p>分段结构属于实现细节而不是可调参数：它不影响对外协议，也不依赖部署环境，
 * 因此保持在代码中并在此说明，不引入配置项。</p>
 */
public final class ObjectKeyGenerator {

    /**
     * 随机目录段的长度（十六进制字符数）。
     */
    private static final int SEGMENT_LENGTH = 2;

    /**
     * 随机目录段的数量。
     */
    private static final int SEGMENT_COUNT = 2;

    /**
     * 日期目录格式，按 UTC 计算，与数据库的 UTC 时间语义一致。
     */
    private static final DateTimeFormatter DATE_DIRECTORY_FORMAT = DateTimeFormatter.ofPattern("uuuu/MM/dd");

    /**
     * 工具类不允许实例化。
     */
    private ObjectKeyGenerator() {
    }

    /**
     * 生成新的对象键。
     *
     * @param extension 已校验的小写扩展名，可为空表示不追加后缀
     * @return 形如 {@code 2026/09/30/1a/2b/<32位随机值>.pdf} 的相对对象键
     */
    public static String generate(String extension) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        StringBuilder objectKey = new StringBuilder();
        objectKey.append(LocalDate.now(ZoneOffset.UTC).format(DATE_DIRECTORY_FORMAT)).append('/');
        int offset = 0;
        for (int index = 0; index < SEGMENT_COUNT; index++) {
            objectKey.append(unique, offset, offset + SEGMENT_LENGTH).append('/');
            offset += SEGMENT_LENGTH;
        }
        objectKey.append(unique);
        if (extension != null && !extension.isEmpty()) {
            objectKey.append('.').append(extension);
        }
        return objectKey.toString();
    }
}
