package cn.orangenode.forge.video.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 片名拼音首字母：常用字、生僻字、英文单词与数字。
 */
class PinyinInitialsTest {

    /**
     * 常用汉字按排序规则取首字母，多音字“长”取片名常用读音。
     */
    @Test
    void commonCharacters() {
        assertThat(PinyinInitials.of("庆余年")).isEqualTo("qyn");
        assertThat(PinyinInitials.of("长相思")).isEqualTo("cxs");
        assertThat(PinyinInitials.of("流人第六季")).isEqualTo("lrdlj");
    }

    /**
     * 排序规则覆盖不到的生僻字由汉字转拼音补全。
     */
    @Test
    void rareCharacters() {
        assertThat(PinyinInitials.of("狂飙")).isEqualTo("kb");
        assertThat(PinyinInitials.of("琅琊榜")).isEqualTo("lyb");
        assertThat(PinyinInitials.of("饕餮记")).isEqualTo("ttj");
    }

    /**
     * 英文单词取首字母，数字保留，符号与空白忽略。
     */
    @Test
    void lettersDigitsAndSymbols() {
        assertThat(PinyinInitials.of("银河护卫队2")).isEqualTo("yhhwd2");
        assertThat(PinyinInitials.of("Friends 老友记")).isEqualTo("flyj");
        assertThat(PinyinInitials.of("《三体》：第1集")).isEqualTo("std1j");
        assertThat(PinyinInitials.of("  ")).isEmpty();
        assertThat(PinyinInitials.of(null)).isEmpty();
    }
}
