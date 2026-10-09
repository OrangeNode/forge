package cn.orangenode.forge.video.collector;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.video.response.SourceCategoryResponse;
import cn.orangenode.forge.video.support.VideoValues;
import lombok.RequiredArgsConstructor;

/**
 * MacCMS JSON 协议解析；页数兼容字符串和数字，不推断不可靠的总集数。
 * 整页结构错误拒绝该页，单条影片的数据异常只记录在快照中，交由入库转入待确认。
 */
@Component
@RequiredArgsConstructor
public class MacCmsParser {
    /** 与 video 表结构一致的存储长度上限，属于表结构不变式而非可调参数。 */
    private static final int UPSTREAM_ID_LIMIT = 128;
    private static final int NAME_LIMIT = 255;
    private static final int REMARKS_LIMIT = 512;
    private static final int DIRECTOR_LIMIT = 2048;
    private static final int LONG_TEXT_LIMIT = 4096;

    private final JsonMapper json;

    /**
     * 校验上游成功码与列表结构。
     */
    public JsonNode root(String content) {
        try {
            JsonNode root = json.readTree(content);
            if (!"1".equals(root.path("code").asText()) || !root.path("list").isArray()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "资源站未返回成功的 MacCMS JSON 列表");
            }
            return root;
        } catch (tools.jackson.core.JacksonException failure) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "资源站返回的 JSON 无法解析");
        }
    }

    /**
     * 提取源分类，缺少父分类时保持为空，交由管理员映射。
     */
    public List<SourceCategoryResponse> categories(String content) {
        JsonNode root = root(content);
        if (!root.path("class").isArray()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "资源站缺少分类列表");
        }
        List<SourceCategoryResponse> result = new ArrayList<>();
        for (JsonNode node : root.path("class")) {
            String id = text(node, "type_id");
            String name = text(node, "type_name");
            if (id == null || name == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "资源站分类缺少编号或名称");
            }
            result.add(new SourceCategoryResponse(id, text(node, "type_pid"), name, null));
        }
        return List.copyOf(result);
    }

    /**
     * 解析完整详情页，列表接口缺少播放地址时拒绝作为详情入库。
     */
    public CollectedPage page(String content, String zone) {
        JsonNode root = root(content);
        int current = root.path("page").asInt(0);
        int count = root.path("pagecount").asInt(-1);
        if (current < 1 || count < 0 || (count > 0 && current > count)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "资源站分页数据不合法");
        }
        List<CollectedVideo> videos = new ArrayList<>();
        for (JsonNode n : root.path("list")) {
            videos.add(video(n, zone));
        }
        if (count == 0 && !videos.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "空分页声明与影片数据矛盾");
        }
        return new CollectedPage(current, count, List.copyOf(videos));
    }

    /**
     * 解析关键词搜索结果；单条影片的数据异常只随结果展示，不影响其他条目。
     */
    public SearchedPage search(String content, String zone) {
        JsonNode root = root(content);
        int current = Math.max(root.path("page").asInt(1), 1);
        int count = Math.max(root.path("pagecount").asInt(0), 0);
        List<SearchedVideo> videos = new ArrayList<>();
        for (JsonNode n : root.path("list")) {
            videos.add(new SearchedVideo(video(n, zone), limit(text(n, "type_name"), NAME_LIMIT, "分类名称", new ArrayList<>())));
        }
        return new SearchedPage(current, count, Math.max(root.path("total").asInt(videos.size()), 0), List.copyOf(videos));
    }

    /**
     * 转换单部影片，缺失匹配字段保持为空供待确认流程判断；源站数据异常记入 problems 而不中断整页。
     */
    private CollectedVideo video(JsonNode n, String zone) {
        List<String> problems = new ArrayList<>();
        String id = text(n, "vod_id");
        if (id == null || id.length() > UPSTREAM_ID_LIMIT) {
            problems.add(id == null ? "影片缺少来源编号" : "影片来源编号过长");
            id = null;
        }
        Integer year = positiveNumber(n, "vod_year");
        Integer total = positiveNumber(n, "vod_total");
        Boolean ended = n.hasNonNull("vod_isend") && !n.path("vod_isend").asText().isBlank()
                ? "1".equals(n.path("vod_isend").asText()) : null;
        LocalDateTime updated = null;
        String sourceTime = text(n, "vod_time");
        if (sourceTime != null) {
            try {
                updated = LocalDateTime.parse(sourceTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        .atZone(ZoneId.of(zone)).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            } catch (java.time.DateTimeException ignored) {
                updated = null;
            }
        }
        List<CollectedLine> lines = lines(text(n, "vod_play_from"), text(n, "vod_play_url"), problems);
        return new CollectedVideo(id, limit(text(n, "type_id"), UPSTREAM_ID_LIMIT, "源站分类编号", problems),
                limit(text(n, "vod_name"), NAME_LIMIT, "名称", problems), year,
                limit(text(n, "vod_area"), NAME_LIMIT, "地区", problems),
                limit(text(n, "vod_lang"), NAME_LIMIT, "语言", problems),
                limit(text(n, "vod_director"), DIRECTOR_LIMIT, "导演", problems),
                limit(text(n, "vod_actor"), LONG_TEXT_LIMIT, "演员", problems),
                limit(text(n, "vod_pic"), LONG_TEXT_LIMIT, "封面地址", problems), plain(text(n, "vod_content")),
                limit(text(n, "vod_remarks"), REMARKS_LIMIT, "更新说明", problems),
                total, ended, updated, lines, problems);
    }

    /**
     * 超出存储长度的字段截断保存并记录异常，避免单条数据拖垮整页。
     */
    private String limit(String value, int max, String label, List<String> problems) {
        if (value == null || value.codePointCount(0, value.length()) <= max) {
            return value;
        }
        problems.add(label + "超过 " + max + " 字已截断");
        return value.substring(0, value.offsetByCodePoints(0, max));
    }

    /**
     * 读取非空文本，保留地址参数及编码。
     */
    private String text(JsonNode node, String key) {
        return node.hasNonNull(key) ? VideoValues.clean(node.path(key).asText()) : null;
    }

    /**
     * 不将零或无法解析的年份及总集数当作有效事实。
     */
    private Integer positiveNumber(JsonNode node, String key) {
        int value = node.path(key).asInt(0);
        return value > 0 ? value : null;
    }

    /**
     * 将来源简介转为纯文本，不执行其中 HTML。
     */
    public String plain(String value) {
        return value == null ? null : value.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", "")
                .replaceAll("<[^>]*>", "").replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").strip();
    }

    /**
     * 按协议分隔线路和分集；异常线路或分集跳过并记入 problems，空结果不表示删除。
     * 只有地址没有名称的分集按位置命名，单集线路命名为“正片”。
     */
    public List<CollectedLine> lines(String from, String urls, List<String> problems) {
        if (urls == null || urls.isBlank()) {
            return List.of();
        }
        if (from == null) {
            problems.add("播放地址缺少线路标识");
            return List.of();
        }
        String[] codes = from.split("\\$\\$\\$", -1);
        String[] groups = urls.split("\\$\\$\\$", -1);
        if (codes.length != groups.length) {
            problems.add("播放线路与地址组数不一致");
            return List.of();
        }
        List<CollectedLine> result = new ArrayList<>();
        java.util.Set<String> usedCodes = new java.util.HashSet<>();
        for (int i = 0; i < codes.length; i++) {
            String code = codes[i].strip();
            if (groups[i].isBlank()) {
                continue;
            }
            if (code.isEmpty() || code.length() > NAME_LIMIT || !usedCodes.add(code)) {
                problems.add(code.isEmpty() ? "第 " + (i + 1) + " 组播放线路标识为空"
                        : code.length() > NAME_LIMIT ? "播放线路标识过长" : "播放线路 " + code + " 重复");
                continue;
            }
            List<String> items = java.util.Arrays.stream(groups[i].split("#", -1))
                    .filter(item -> !item.isBlank()).toList();
            List<CollectedEpisode> episodes = new ArrayList<>();
            for (int j = 0; j < items.size(); j++) {
                String item = items.get(j).strip();
                int separator = item.indexOf('$');
                String name = separator < 0 ? "" : item.substring(0, separator).strip();
                String url = (separator < 0 ? item : item.substring(separator + 1)).strip();
                if (url.isEmpty() || (name.isEmpty() && !url.contains("://"))) {
                    problems.add("线路 " + code + " 第 " + (j + 1) + " 集缺少地址");
                    continue;
                }
                if (name.isEmpty()) {
                    name = items.size() == 1 ? "正片" : "第" + (j + 1) + "集";
                }
                episodes.add(new CollectedEpisode(limit(name, NAME_LIMIT, "分集名称", problems), url, urlType(url)));
            }
            if (!episodes.isEmpty()) {
                result.add(new CollectedLine(code, List.copyOf(episodes)));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 仅按 HTTP 地址路径识别媒体类型，网页线路保持 OTHER。
     */
    private String urlType(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                return "OTHER";
            }
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            return path.endsWith(".m3u8") ? "M3U8" : path.endsWith(".mp4") ? "MP4" : "OTHER";
        } catch (IllegalArgumentException failure) {
            return "OTHER";
        }
    }
}
