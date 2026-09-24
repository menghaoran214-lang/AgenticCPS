package com.qiji.cps.module.cps.client.haina;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiji.cps.module.ai.service.capability.McpRegistryService;
import com.qiji.cps.module.cps.client.haina.dto.HainaDecisionEvidence;
import com.qiji.cps.module.cps.client.haina.dto.HainaDecisionRequest;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class HainaMcpDecisionClient implements HainaDecisionClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SERVER_CODE = "haina";
    private static final String CONTENT_SEARCH_TOOL = "content_search";

    private final HainaDecisionProperties properties;
    private final McpRegistryService mcpRegistryService;

    public HainaMcpDecisionClient(HainaDecisionProperties properties, McpRegistryService mcpRegistryService) {
        this.properties = properties;
        this.mcpRegistryService = mcpRegistryService;
    }

    @Override
    public HainaDecisionEvidence collectEvidence(HainaDecisionRequest request) {
        try {
            String keyword = buildKeyword(request);
            return HainaDecisionEvidence.builder().available(true)
                    .discounts(parseContentEvidence(callTool(CONTENT_SEARCH_TOOL, buildContentSearchArgs(keyword, request))))
                    .build();
        } catch (Exception e) {
            log.warn("[HainaMcpDecisionClient] 海纳 MCP 调用失败: {}", e.getMessage());
            return HainaDecisionEvidence.unavailable("海纳 MCP 暂不可用，已使用 CPS 自有商品数据生成建议");
        }
    }

    private Map<String, Object> buildContentSearchArgs(String keyword, HainaDecisionRequest request) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", keyword);
        args.put("return_size", normalizeMaxResults(request));
        args.put("query_rewrite", false);
        args.put("correlate_enhance", true);
        return args;
    }

    private int normalizeMaxResults(HainaDecisionRequest request) {
        int configured = Math.max(1, properties.getMaxResults());
        if (request == null || request.getMaxResults() == null) {
            return Math.min(configured, 10);
        }
        return Math.max(1, Math.min(Math.min(request.getMaxResults(), configured), 100));
    }

    private String buildKeyword(HainaDecisionRequest request) {
        StringBuilder builder = new StringBuilder();
        if (request != null && StringUtils.hasText(request.getNeed())) {
            builder.append(request.getNeed().trim());
        }
        if (request != null && StringUtils.hasText(request.getScenario())) {
            builder.append(' ').append(request.getScenario().trim());
        }
        return builder.toString().trim();
    }

    private String callTool(String toolName, Map<String, Object> args) {
        McpSchema.CallToolResult result = mcpRegistryService.callToolByCode(SERVER_CODE, toolName, args);
        if (Boolean.TRUE.equals(result.isError())) {
            throw new IllegalStateException("haina tool error: " + toolName);
        }
        if (result.structuredContent() != null) {
            return toJson(result.structuredContent());
        }
        if (CollectionUtils.isEmpty(result.content())) {
            return "{}";
        }
        List<String> texts = new ArrayList<>();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent && StringUtils.hasText(textContent.text())) {
                texts.add(textContent.text());
            }
        }
        return texts.isEmpty() ? "{}" : String.join("\n", texts);
    }

    /** Maps the new content-search articles to the existing read-only evidence shape. */
    private List<HainaDecisionEvidence.DiscountEvidence> parseContentEvidence(String raw) {
        JsonNode root = readTree(raw);
        JsonNode data = root.path("data");
        JsonNode items = data.path("content_list");
        if (!items.isArray()) {
            items = data.path("items");
        }
        if (!items.isArray()) {
            items = root.path("content_list");
        }
        if (!items.isArray()) {
            items = root.path("items");
        }
        if (!items.isArray()) {
            return Collections.emptyList();
        }
        List<HainaDecisionEvidence.DiscountEvidence> evidence = new ArrayList<>();
        for (JsonNode item : items) {
            evidence.add(HainaDecisionEvidence.DiscountEvidence.builder()
                    .title(firstText(item, "works_title", "title"))
                    .mallName(firstText(item, "website_cname", "source", "icon"))
                    .content(limit(firstText(item, "works_content", "context"), 160))
                    .url(firstText(item, "works_link", "article_link"))
                    .pubdate(text(item, "publish_time"))
                    .build());
            if (evidence.size() >= Math.max(1, properties.getMaxResults())) {
                break;
            }
        }
        return evidence;
    }

    private JsonNode readTree(String raw) {
        try {
            return OBJECT_MAPPER.readTree(StringUtils.hasText(raw) ? raw : "{}");
        } catch (Exception e) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = text(node, name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
