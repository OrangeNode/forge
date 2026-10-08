package cn.orangenode.forge.app.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;

/**
 * 读取 APK 的包名、版本号与版本名，并计算整个文件的 SHA-256。
 *
 * <p>APK 是 ZIP 包，其中 {@code AndroidManifest.xml} 为 Android 二进制 XML：依次是字符串池、资源 ID 表与元素块。
 * 本类只解析第一个 {@code manifest} 元素的三个属性：{@code package}、{@code versionCode}（资源 ID 0x0101021b）
 * 与 {@code versionName}（0x0101021c），属性优先按资源 ID 识别，名称被混淆时也能读到。
 * 只顺序读取一遍输入流，同时计算摘要，不把整个安装包载入内存。</p>
 */
public final class ApkInspector {

    /**
     * 清单文件在 APK 中的路径。
     */
    private static final String MANIFEST = "AndroidManifest.xml";

    /**
     * 清单文件大小上限，防止异常包占用过多内存。
     */
    private static final int MANIFEST_MAX_BYTES = 4 * 1024 * 1024;

    /**
     * 二进制 XML 块类型：字符串池。
     */
    private static final int CHUNK_STRING_POOL = 0x0001;

    /**
     * 二进制 XML 块类型：资源 ID 表。
     */
    private static final int CHUNK_RESOURCE_MAP = 0x0180;

    /**
     * 二进制 XML 块类型：元素开始。
     */
    private static final int CHUNK_START_ELEMENT = 0x0102;

    /**
     * 字符串池 UTF-8 标志位。
     */
    private static final int UTF8_FLAG = 0x100;

    /**
     * {@code android:versionCode} 的资源 ID。
     */
    private static final int ATTR_VERSION_CODE = 0x0101021b;

    /**
     * {@code android:versionName} 的资源 ID。
     */
    private static final int ATTR_VERSION_NAME = 0x0101021c;

    /**
     * 属性值类型：字符串。
     */
    private static final int TYPE_STRING = 0x03;

    /**
     * 属性值类型：十进制或十六进制整数的起止范围。
     */
    private static final int TYPE_INT_DEC = 0x10;

    /**
     * 属性值类型：十六进制整数。
     */
    private static final int TYPE_INT_HEX = 0x11;

    /**
     * 工具类不允许实例化。
     */
    private ApkInspector() {
    }

    /**
     * 解析结果。
     *
     * @param packageName 包名
     * @param versionCode 版本号
     * @param versionName 版本名，可为空
     * @param sha256      整个文件的 SHA-256（小写十六进制）
     * @param sizeBytes   文件字节数
     */
    public record ApkInfo(String packageName, int versionCode, String versionName, String sha256, long sizeBytes) {
    }

    /**
     * 顺序读取 APK 并解析清单与摘要；调用方负责关闭输入流。
     *
     * @param input APK 内容
     * @return 解析结果
     * @throws BusinessException 不是有效 APK 或清单缺少版本信息时抛出 400
     */
    public static ApkInfo inspect(InputStream input) {
        MessageDigest digest = sha256();
        CountingStream counting = new CountingStream(new DigestInputStream(input, digest));
        byte[] manifest = null;
        try {
            ZipInputStream zip = new ZipInputStream(counting);
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (manifest == null && MANIFEST.equals(entry.getName())) {
                    manifest = readLimited(zip);
                }
            }
            counting.transferTo(java.io.OutputStream.nullOutputStream());
        } catch (IOException | IllegalArgumentException failure) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不是有效的 APK 安装包");
        }
        if (manifest == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不是有效的 APK 安装包（缺少 AndroidManifest.xml）");
        }
        Manifest parsed = parseManifest(manifest);
        return new ApkInfo(parsed.packageName, parsed.versionCode, parsed.versionName,
                HexFormat.of().formatHex(digest.digest()), counting.count);
    }

    /**
     * 解析二进制清单中的 manifest 元素属性。
     *
     * @param data 二进制清单内容
     * @return 包名与版本信息
     */
    static Manifest parseManifest(byte[] data) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int offset = buffer.getShort(2) & 0xffff;
            String[] strings = new String[0];
            int[] resourceIds = new int[0];
            while (offset + 8 <= data.length) {
                int type = buffer.getShort(offset) & 0xffff;
                int headerSize = buffer.getShort(offset + 2) & 0xffff;
                int size = buffer.getInt(offset + 4);
                if (size <= 0) {
                    break;
                }
                if (type == CHUNK_STRING_POOL) {
                    strings = readStringPool(buffer, offset);
                } else if (type == CHUNK_RESOURCE_MAP) {
                    resourceIds = new int[(size - headerSize) / 4];
                    for (int i = 0; i < resourceIds.length; i++) {
                        resourceIds[i] = buffer.getInt(offset + headerSize + i * 4);
                    }
                } else if (type == CHUNK_START_ELEMENT) {
                    Manifest manifest = readManifestElement(buffer, offset, headerSize, strings, resourceIds);
                    if (manifest != null) {
                        return manifest;
                    }
                }
                offset += size;
            }
        } catch (IndexOutOfBoundsException failure) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "安装包清单格式无法识别");
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "安装包清单缺少 manifest 元素");
    }

    /**
     * 读取元素开始块；不是 manifest 元素时返回空。
     *
     * @param buffer      清单内容
     * @param offset      块起始位置
     * @param headerSize  块头长度
     * @param strings     字符串池
     * @param resourceIds 资源 ID 表
     * @return manifest 元素的属性，不是 manifest 时为空
     */
    private static Manifest readManifestElement(ByteBuffer buffer, int offset, int headerSize, String[] strings,
            int[] resourceIds) {
        int ext = offset + headerSize;
        if (!"manifest".equals(string(strings, buffer.getInt(ext + 4)))) {
            return null;
        }
        int attributeStart = buffer.getShort(ext + 8) & 0xffff;
        int attributeSize = buffer.getShort(ext + 10) & 0xffff;
        int attributeCount = buffer.getShort(ext + 12) & 0xffff;
        String packageName = null;
        Integer versionCode = null;
        String versionName = null;
        for (int i = 0; i < attributeCount; i++) {
            int at = ext + attributeStart + i * attributeSize;
            int nameIndex = buffer.getInt(at + 4);
            int rawValue = buffer.getInt(at + 8);
            int dataType = buffer.get(at + 15) & 0xff;
            int data = buffer.getInt(at + 16);
            int resourceId = nameIndex >= 0 && nameIndex < resourceIds.length ? resourceIds[nameIndex] : 0;
            String name = string(strings, nameIndex);
            String text = dataType == TYPE_STRING ? string(strings, rawValue >= 0 ? rawValue : data)
                    : string(strings, rawValue);
            if (resourceId == ATTR_VERSION_CODE || resourceId == 0 && "versionCode".equals(name)) {
                if (dataType == TYPE_INT_DEC || dataType == TYPE_INT_HEX) {
                    versionCode = data;
                } else if (text != null) {
                    versionCode = Integer.valueOf(text.strip());
                }
            } else if (resourceId == ATTR_VERSION_NAME || resourceId == 0 && "versionName".equals(name)) {
                versionName = text;
            } else if (resourceId == 0 && "package".equals(name)) {
                packageName = text;
            }
        }
        if (packageName == null || versionCode == null || versionCode <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "安装包清单缺少包名或版本号");
        }
        return new Manifest(packageName, versionCode, versionName);
    }

    /**
     * 读取字符串池。
     *
     * @param buffer 清单内容
     * @param offset 字符串池块起始位置
     * @return 全部字符串
     */
    private static String[] readStringPool(ByteBuffer buffer, int offset) {
        int headerSize = buffer.getShort(offset + 2) & 0xffff;
        int count = buffer.getInt(offset + 8);
        int flags = buffer.getInt(offset + 16);
        int stringsStart = buffer.getInt(offset + 20);
        boolean utf8 = (flags & UTF8_FLAG) != 0;
        String[] result = new String[count];
        for (int i = 0; i < count; i++) {
            int at = offset + stringsStart + buffer.getInt(offset + headerSize + i * 4);
            result[i] = utf8 ? readUtf8(buffer, at) : readUtf16(buffer, at);
        }
        return result;
    }

    /**
     * 读取 UTF-8 字符串：先是字符数与字节数（各 1 或 2 字节），再是内容。
     *
     * @param buffer 清单内容
     * @param at     字符串起始位置
     * @return 字符串
     */
    private static String readUtf8(ByteBuffer buffer, int at) {
        int position = at;
        int first = buffer.get(position++) & 0xff;
        if ((first & 0x80) != 0) {
            position++;
        }
        int length = buffer.get(position++) & 0xff;
        if ((length & 0x80) != 0) {
            length = ((length & 0x7f) << 8) | (buffer.get(position++) & 0xff);
        }
        byte[] bytes = new byte[length];
        buffer.get(position, bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 读取 UTF-16 字符串：先是字符数（1 或 2 个 16 位），再是内容。
     *
     * @param buffer 清单内容
     * @param at     字符串起始位置
     * @return 字符串
     */
    private static String readUtf16(ByteBuffer buffer, int at) {
        int position = at;
        int length = buffer.getShort(position) & 0xffff;
        position += 2;
        if ((length & 0x8000) != 0) {
            length = ((length & 0x7fff) << 16) | (buffer.getShort(position) & 0xffff);
            position += 2;
        }
        byte[] bytes = new byte[length * 2];
        buffer.get(position, bytes);
        return new String(bytes, StandardCharsets.UTF_16LE);
    }

    /**
     * 按下标读取字符串，下标越界或为 -1 时返回空。
     *
     * @param strings 字符串池
     * @param index   下标
     * @return 字符串
     */
    private static String string(String[] strings, int index) {
        return index >= 0 && index < strings.length ? strings[index] : null;
    }

    /**
     * 读取当前 ZIP 条目，超过上限时拒绝。
     *
     * @param zip ZIP 输入流
     * @return 条目内容
     * @throws IOException 读取失败
     */
    private static byte[] readLimited(ZipInputStream zip) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) > 0) {
            out.write(buffer, 0, read);
            if (out.size() > MANIFEST_MAX_BYTES) {
                throw new IOException("清单过大");
            }
        }
        return out.toByteArray();
    }

    /**
     * 创建 SHA-256 摘要器。
     *
     * @return 摘要器
     */
    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("运行环境缺少 SHA-256", impossible);
        }
    }

    /**
     * 清单中的包名与版本信息。
     *
     * @param packageName 包名
     * @param versionCode 版本号
     * @param versionName 版本名
     */
    record Manifest(String packageName, int versionCode, String versionName) {
    }

    /**
     * 统计读取字节数的输入流。
     */
    private static final class CountingStream extends java.io.FilterInputStream {

        /**
         * 已读取字节数。
         */
        private long count;

        /**
         * 包装输入流。
         *
         * @param in 被包装的输入流
         */
        CountingStream(InputStream in) {
            super(in);
        }

        /**
         * 读取单个字节并计数。
         *
         * @return 字节值，结束时为 -1
         * @throws IOException 读取失败
         */
        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                count++;
            }
            return value;
        }

        /**
         * 读取到缓冲区并计数。
         *
         * @param buffer 缓冲区
         * @param offset 起始位置
         * @param length 最大长度
         * @return 实际读取字节数，结束时为 -1
         * @throws IOException 读取失败
         */
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                count += read;
            }
            return read;
        }

        /**
         * ZIP 读取结束后外层不能关闭原始流，交给调用方关闭。
         */
        @Override
        public void close() {
            // 由调用方关闭原始流
        }
    }
}
