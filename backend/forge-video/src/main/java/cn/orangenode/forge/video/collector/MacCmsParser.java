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
 */
@Component
@RequiredArgsConstructor
public class MacCmsParser {
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
     * 转换单部影片，缺失匹配字段保持为空供待确认流程判断。
     */
    private CollectedVideo video(JsonNode n, String zone) {
        String id = text(n, "vod_id");
        if (id == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "影片缺少来源编号");
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
        return new CollectedVideo(id, text(n, "type_id"), text(n, "vod_name"), year,
                text(n, "vod_area"), text(n, "vod_lang"), text(n, "vod_director"), text(n, "vod_actor"),
                text(n, "vod_pic"), plain(text(n, "vod_content")), text(n, "vod_remarks"),
                total, ended, updated, lines(text(n, "vod_play_from"), text(n, "vod_play_url")));
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
     * 按协议分隔线路和分集，异常线路拒绝整页写入，空结果不表示删除。
     */
    public List<CollectedLine> lines(String from, String urls) {
        if (urls == null || urls.isBlank()) {
            return List.of();
        }
        if (from == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "播放地址缺少线路标识");
        }
        String[] codes = from.split("\\$\\$\\$", -1);
        String[] groups = urls.split("\\$\\$\\$", -1);
        if (codes.length != groups.length) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "播放线路与地址组数不一致");
        }
        List<CollectedLine> result = new ArrayList<>();
        java.util.Set<String> usedCodes = new java.util.HashSet<>();
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].isBlank() || !usedCodes.add(codes[i].strip())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "播放线路标识为空或重复");
            }
            List<CollectedEpisode> episodes = new ArrayList<>();
            if (!groups[i].isBlank()) {
                for (String item : groups[i].split("#", -1)) {
                    int separator = item.indexOf('$');
                    if (separator < 1 || separator == item.length() - 1) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "分集格式必须是名称和地址");
                    }
                    String url = item.substring(separator + 1).strip();
                    episodes.add(new CollectedEpisode(item.substring(0, separator).strip(), url, urlType(url)));
                }
            }
            if (!episodes.isEmpty()) {
                result.add(new CollectedLine(codes[i].strip(), List.copyOf(episodes)));
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
