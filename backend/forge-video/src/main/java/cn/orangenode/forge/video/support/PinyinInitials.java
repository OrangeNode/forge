package cn.orangenode.forge.video.support;

import java.text.Collator;
import java.util.Locale;

import com.ibm.icu.text.Transliterator;

/**
 * 片名拼音首字母。
 *
 * <p>常用汉字（GB2312 一级字）按 JDK 中文排序规则定位首字母，对“长”“行”等多音字更贴近片名常用读音；
 * 其余汉字用 ICU 的汉字转拼音补全。英文单词取首字母，数字原样保留，其他符号忽略。
 * 多音字无法保证与用户输入一致，因此搜索同时保留片名包含匹配。</p>
 */
public final class PinyinInitials {

    /**
     * 各声母区间的起始汉字，与 {@link #LETTERS} 一一对应（拼音没有 i、u、v 开头的音节）。
     */
    private static final String BOUNDS = "阿芭擦搭蛾发噶哈击喀垃妈拿哦啪期然撒塌挖昔压匝";

    /**
     * 区间对应的首字母。
     */
    private static final String LETTERS = "abcdefghjklmnopqrstwxyz";

    /**
     * 中文排序规则覆盖的最后一个汉字。
     */
    private static final String LAST = "座";

    /**
     * 排序器不是线程安全的，每个线程一份。
     */
    private static final ThreadLocal<Collator> COLLATOR =
            ThreadLocal.withInitial(() -> Collator.getInstance(Locale.CHINA));

    /**
     * 汉字转拼音，ICU 转写器不保证线程安全，每个线程一份。
     */
    private static final ThreadLocal<Transliterator> PINYIN =
            ThreadLocal.withInitial(() -> Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower"));

    /**
     * 工具类不允许实例化。
     */
    private PinyinInitials() {
    }

    /**
     * 计算片名首字母，例如“庆余年第二季”得到 {@code qyndejj}，“银河护卫队2”得到 {@code yhhwd2}。
     *
     * @param name 片名，可为空
     * @return 小写首字母串，超过 255 个字符截断；没有可识别字符时返回空串
     */
    public static String of(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        boolean inWord = false;
        for (int index = 0; index < name.length(); index++) {
            char ch = name.charAt(index);
            if (ch < 128 && Character.isLetter(ch)) {
                if (!inWord) {
                    result.append(Character.toLowerCase(ch));
                }
                inWord = true;
                continue;
            }
            inWord = false;
            if (ch < 128 && Character.isDigit(ch)) {
                result.append(ch);
            } else if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                char initial = initial(ch);
                if (initial != 0) {
                    result.append(initial);
                }
            }
        }
        return result.length() > 255 ? result.substring(0, 255) : result.toString();
    }

    /**
     * 单个汉字的首字母，无法识别时返回 0。
     *
     * @param ch 汉字
     * @return 小写首字母或 0
     */
    private static char initial(char ch) {
        String text = String.valueOf(ch);
        Collator collator = COLLATOR.get();
        if (collator.compare(text, BOUNDS.substring(0, 1)) >= 0 && collator.compare(text, LAST) <= 0) {
            for (int index = BOUNDS.length() - 1; index >= 0; index--) {
                if (collator.compare(text, BOUNDS.substring(index, index + 1)) >= 0) {
                    return LETTERS.charAt(index);
                }
            }
        }
        String pinyin = PINYIN.get().transliterate(text).strip();
        return !pinyin.isEmpty() && pinyin.charAt(0) >= 'a' && pinyin.charAt(0) <= 'z' ? pinyin.charAt(0) : 0;
    }
}
