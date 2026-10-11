package cn.orangenode.forge.app.support;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * 公告 HTML 白名单过滤：只保留常见排版标签、图片与链接，脚本、样式、事件属性与非 http(s) 地址全部去掉。
 *
 * <p>站内相对地址（如文件管理外链 {@code /api/public/v1/files/...}）保留为相对地址，由 App 拼接接口基础地址。</p>
 */
public final class AnnouncementHtml {

    /**
     * 解析相对地址时使用的占位基础地址，只用于协议校验，不会写进结果。
     */
    private static final String PLACEHOLDER_BASE = "https://announcement.invalid/";

    /**
     * 允许的标签与属性；与 App 端渲染器支持的范围一致。
     */
    private static final Safelist SAFELIST = new Safelist()
            .addTags("p", "br", "div", "span", "b", "strong", "i", "em", "u", "s", "del", "small",
                    "h1", "h2", "h3", "h4", "ul", "ol", "li", "blockquote", "hr", "a", "img", "font")
            .addAttributes("a", "href")
            .addAttributes("img", "src", "alt", "width", "height")
            .addAttributes("font", "color")
            .addProtocols("a", "href", "http", "https")
            .addProtocols("img", "src", "http", "https")
            .preserveRelativeLinks(true);

    /**
     * 工具类不允许实例化。
     */
    private AnnouncementHtml() {
    }

    /**
     * 按白名单过滤 HTML 片段。
     *
     * @param html 管理员填写的 HTML
     * @return 过滤后的 HTML 片段
     */
    public static String sanitize(String html) {
        Document.OutputSettings output = new Document.OutputSettings().prettyPrint(false);
        return Jsoup.clean(html == null ? "" : html, PLACEHOLDER_BASE, SAFELIST, output).strip();
    }

    /**
     * 判断过滤后的 HTML 是否还有可显示的内容（文字或图片）。
     *
     * @param html 过滤后的 HTML
     * @return 有文字或图片时为 {@code true}
     */
    public static boolean hasContent(String html) {
        Document document = Jsoup.parseBodyFragment(html);
        return !document.body().text().isBlank() || !document.body().select("img").isEmpty();
    }
}
