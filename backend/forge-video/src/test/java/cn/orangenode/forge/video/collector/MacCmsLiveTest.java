package cn.orangenode.forge.video.collector;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;
import cn.orangenode.forge.video.config.VideoProperties;

/**
 * 显式开启的四站 Java 只读探针，仅取分类与日周各第一页，不遍历或下载视频。
 */
@EnabledIfEnvironmentVariable(named = "FORGE_VIDEO_LIVE_TESTS", matches = "true")
class MacCmsLiveTest {
    /**
     * 用实际 Java 客户端验证四站协议和完整线路解析。
     */
    @Test
    void checksFourSourcesWithBoundedRequests() {
        VideoProperties properties = new VideoProperties();
        properties.setConnectTimeoutSeconds(10);
        properties.setRequestTimeoutSeconds(30);
        properties.setRetryCount(0);
        properties.setUserAgent("OrangeForge-VideoCollector/1.0");
        properties.setProxyUrl(System.getenv("FORGE_VIDEO_PROXY_URL"));
        properties.setMaxResponseBytes(10 * 1024 * 1024);
        MacCmsClient client = new MacCmsClient(properties);
        MacCmsParser parser = new MacCmsParser(JsonMapper.builder().build());
        for (String api : List.of("https://cj.lziapi.com/api.php/provide/vod/",
                "https://cj.ffzyapi.com/api.php/provide/vod/", "https://api.guangsuapi.com/api.php/provide/vod/",
                "https://www.hongniuzy2.com/api.php/provide/vod/")) {
            assertThat(parser.categories(client.get(client.url(api, "list", null, null, 1)))).isNotEmpty();
            for (String range : List.of("DAY", "WEEK")) {
                CollectedPage page = parser.page(client.get(client.url(api, "detail", range, null, 1)), "Asia/Shanghai");
                assertThat(page.page()).isEqualTo(1);
                assertThat(page.videos()).isNotEmpty();
                assertThat(page.videos()).anyMatch(v -> !v.lines().isEmpty());
            }
        }
    }
}
