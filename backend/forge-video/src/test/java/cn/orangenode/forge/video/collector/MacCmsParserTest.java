package cn.orangenode.forge.video.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.video.config.VideoProperties;
import cn.orangenode.forge.video.support.VideoValues;

/**
 * 协议解析、保守归一化及独立时间范围的验证。
 */
class MacCmsParserTest {
    private final MacCmsParser parser = new MacCmsParser(JsonMapper.builder().build());

    /**
     * 缺少名称交给字段策略处理，缺少来源编号记为异常而不拒绝整页。
     */
    @Test
    void retainsMissingNameForConfiguredPolicy() {
        var page = parser.page("{\"code\":1,\"page\":1,\"pagecount\":1,\"list\":[{\"vod_id\":1}]}", "Asia/Shanghai");
        assertThat(page.videos().getFirst().name()).isNull();
        assertThat(page.videos().getFirst().problems()).isEmpty();
        var missingId = parser.page("{\"code\":1,\"page\":1,\"pagecount\":1,\"list\":[{\"vod_name\":\"影片\"}]}",
                "Asia/Shanghai").videos().getFirst();
        assertThat(missingId.upstreamId()).isNull();
        assertThat(missingId.problems()).containsExactly("影片缺少来源编号");
    }

    /**
     * 数字与字符串分页兼容，完整保留签名地址且不推断完结。
     */
    @Test
    void parsesMixedPaginationAndEveryLine() {
        CollectedPage page = parser.page("""
                {"code":1,"page":"1","pagecount":1,"list":[{"vod_id":123,"type_id":"6",
                "vod_name":"测试 第一季","vod_year":"2026","vod_total":"1",
                "vod_content":"<p>说明&amp;介绍</p><script>alert(1)</script>",
                "vod_play_from":"web$$$m3u8$$$mp4",
                "vod_play_url":"正片$https://example.com/play?id=1$$$第一集$https://example.com/a.m3u8?token=A%2FB&x=$ok#第二集$https://example.com/b.m3u8$$$正片$https://example.com/a.mp4"}]}
                """, "Asia/Shanghai");
        CollectedVideo video = page.videos().getFirst();
        assertThat(video.upstreamId()).isEqualTo("123");
        assertThat(video.ended()).isNull();
        assertThat(video.declaredTotal()).isEqualTo(1);
        assertThat(video.description()).isEqualTo("说明&介绍");
        assertThat(video.lines()).hasSize(3);
        assertThat(video.lines().get(1).episodes().getFirst().url())
                .isEqualTo("https://example.com/a.m3u8?token=A%2FB&x=$ok");
        assertThat(video.lines().stream().map(l -> l.episodes().getFirst().urlType()).toList())
                .containsExactly("OTHER", "M3U8", "MP4");
    }

    /**
     * 异常线路和分集只跳过并记录，整页结构错误仍拒绝，空地址不代表清除已有分集。
     */
    @Test
    void recordsMalformedLineWithoutInventingDeletion() {
        List<String> problems = new ArrayList<>();
        assertThat(parser.lines("m3u8", null, problems)).isEmpty();
        assertThat(parser.lines("web$$$m3u8", "$$$第一集$https://example.com/a.m3u8", problems)).hasSize(1);
        assertThat(problems).isEmpty();
        assertThat(parser.lines("a$$$b", "第一集$https://example.com/a", problems)).isEmpty();
        assertThat(problems).containsExactly("播放线路与地址组数不一致");
        problems.clear();
        List<CollectedLine> partial = parser.lines("a$$$a", "缺少地址#第二集$#第三集$https://example.com/c.m3u8"
                + "$$$第一集$https://example.com/a", problems);
        assertThat(partial).hasSize(1);
        assertThat(partial.getFirst().episodes()).extracting(CollectedEpisode::name).containsExactly("第三集");
        assertThat(problems).containsExactly("线路 a 第 1 集缺少地址", "线路 a 第 2 集缺少地址", "播放线路 a 重复");
        assertThatThrownBy(() -> parser.page("{\"code\":0,\"list\":[]}", "Asia/Shanghai"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> parser.page("{\"code\":1,\"page\":1,\"pagecount\":0,\"list\":[{}]}", "Asia/Shanghai"))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 只有地址没有名称的分集按位置命名，末尾多余分隔符不算异常，超长字段截断并记录。
     */
    @Test
    void namesUrlOnlyEpisodesAndTruncatesOverlongFields() {
        List<String> problems = new ArrayList<>();
        List<CollectedLine> single = parser.lines("lzm3u8", "https://example.com/share/1#", problems);
        assertThat(single.getFirst().episodes()).extracting(CollectedEpisode::name).containsExactly("正片");
        List<CollectedLine> many = parser.lines("lzm3u8", "$https://example.com/1.m3u8#https://example.com/2.m3u8",
                problems);
        assertThat(many.getFirst().episodes()).extracting(CollectedEpisode::name).containsExactly("第1集", "第2集");
        assertThat(problems).isEmpty();
        CollectedVideo video = parser.page("{\"code\":1,\"page\":1,\"pagecount\":1,\"list\":[{\"vod_id\":1,"
                + "\"vod_name\":\"" + "长".repeat(300) + "\"}]}", "Asia/Shanghai").videos().getFirst();
        assertThat(video.name()).hasSize(255);
        assertThat(video.problems()).containsExactly("名称超过 255 字已截断");
    }

    /**
     * 范围链接替换旧保留参数，保留业务参数编码并排除月范围。
     */
    @Test
    void buildsOnlyDayWeekAndAllUrls() {
        MacCmsClient client = new MacCmsClient(new VideoProperties());
        String base = "https://example.com/api.php?key=A%2FB&h=1&ac=list&pg=99";
        assertThat(client.url(base, "detail", "DAY", "6", 1)).endsWith("key=A%2FB&ac=detail&pg=1&h=24&t=6");
        assertThat(client.url(base, "detail", "WEEK", null, 2)).endsWith("ac=detail&pg=2&h=168");
        assertThat(client.url(base, "detail", "ALL", null, 1)).doesNotContain("h=");
        assertThatThrownBy(() -> client.url(base, "detail", "MONTH", null, 1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> client.validateUrl("http://127.0.0.1/api")).isInstanceOf(BusinessException.class);
    }

    /**
     * 代取海报与采集共用地址校验，内网、本机与非 HTTP 地址在发起请求前即被拒绝。
     */
    @Test
    void posterRejectsPrivateAndNonHttpUrls() {
        VideoProperties properties = new VideoProperties();
        properties.setConnectTimeoutSeconds(1);
        properties.setRequestTimeoutSeconds(1);
        properties.setMaxPosterBytes(1024);
        properties.setUserAgent("test");
        MacCmsClient client = new MacCmsClient(properties);
        assertThatThrownBy(() -> client.image("http://127.0.0.1/poster.jpg")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> client.image("http://192.168.1.10/poster.jpg")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> client.image("file:///etc/passwd")).isInstanceOf(BusinessException.class);
    }

    /**
     * 人员按集合比较，名称保留年份和季数差异。
     */
    @Test
    void normalizesOnlySafeDifferences() {
        assertThat(VideoValues.normalize(" ＡＢＣ　第一季 ")).isEqualTo(VideoValues.normalize("abc 第一季"));
        assertThat(VideoValues.normalize("第一季")).isNotEqualTo(VideoValues.normalize("第二季"));
        assertThat(VideoValues.people("张三，李四,张三")).isEqualTo(VideoValues.people("李四,张三"));
    }

    /**
     * 五段 cron 按调度时区计算，补触发后只得到一个未来时间，非法表达式明确拒绝。
     */
    @Test
    void calculatesNextTimeFromCron() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDateTime now = LocalDateTime.of(2026, 9, 30, 17, 0);
        assertThat(TaskSchedules.next("0 2 * * *", zone, now)).isEqualTo(LocalDateTime.of(2026, 9, 30, 18, 0));
        assertThat(TaskSchedules.next("0 2 * * 1", zone, now)).isEqualTo(LocalDateTime.of(2026, 10, 4, 18, 0));
        assertThat(TaskSchedules.next("0 */3 * * *", zone, now)).isEqualTo(LocalDateTime.of(2026, 9, 30, 19, 0));
        assertThatThrownBy(() -> TaskSchedules.next("0 2 * *", zone, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TaskSchedules.next("0 25 * * *", zone, now)).isInstanceOf(IllegalArgumentException.class);
    }
}
