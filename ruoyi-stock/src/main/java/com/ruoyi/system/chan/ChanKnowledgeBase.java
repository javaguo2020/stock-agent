package com.ruoyi.system.chan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.system.chan2.Chan2Result;
import com.ruoyi.system.domain.StockChanKnowledge;
import com.ruoyi.system.mapper.StockChanKnowledgeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 本地缠论知识库。对应 Python knowledge_base/kb.py。
 *
 * 数据源：classpath:/chan/concepts.json 与 /chan/rules.json（可手工编辑）。
 * 提供多关键词打分检索（中文子串匹配，无需分词器）与结合分析结果的解释推荐。
 */
@Slf4j
@Component
public class ChanKnowledgeBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private StockChanKnowledgeMapper knowledgeMapper;

    private List<Map<String, Object>> concepts = new ArrayList<>();
    private List<Map<String, Object>> rules = new ArrayList<>();

    @PostConstruct
    public void init() {
        try {
            concepts = load("/chan/concepts.json");
            rules = load("/chan/rules.json");
            log.info("缠论知识库JSON加载完成：概念 {} 条，规则 {} 条", concepts.size(), rules.size());
        } catch (Exception e) {
            log.warn("缠论知识库JSON加载失败（不影响主分析流程）：{}", e.getMessage());
            concepts = new ArrayList<>();
            rules = new ArrayList<>();
        }
        if (knowledgeMapper == null) {
            log.warn("缠论知识库数据库Mapper不可用，仅使用JSON数据");
            return;
        }
        try {
            if (!loadFromDb()) {
                migrateSeedsToDb();
                if (!loadFromDb()) {
                    log.info("缠论知识库数据库为空，暂用内存/JSON数据（共 {} 条）", concepts.size() + rules.size());
                }
            }
        } catch (Exception e) {
            log.warn("缠论知识库数据库加载/迁移失败，退回JSON数据：{}", e.getMessage());
        }
    }

    /**
     * 热刷新：管理页面增删改后调用，重新从数据库加载内存知识库。
     */
    public void reload() {
        try {
            if (knowledgeMapper != null && loadFromDb()) {
                return;
            }
        } catch (Exception e) {
            log.warn("缠论知识库热刷新失败，保留原数据：{}", e.getMessage());
        }
    }

    private boolean loadFromDb() {
        List<StockChanKnowledge> rows = knowledgeMapper.selectAllEnabled();
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        List<Map<String, Object>> c = new ArrayList<>();
        List<Map<String, Object>> r = new ArrayList<>();
        for (StockChanKnowledge k : rows) {
            if ("rule".equals(k.getKind())) {
                r.add(convertToMap(k));
            } else {
                c.add(convertToMap(k));
            }
        }
        this.concepts = c;
        this.rules = r;
        log.info("缠论知识库已从数据库加载：概念 {} 条，规则 {} 条", c.size(), r.size());
        return true;
    }

    private void migrateSeedsToDb() {
        int n = 0;
        for (Map<String, Object> e : concepts) {
            knowledgeMapper.insertStockChanKnowledge(toRow(e, "concept"));
            n++;
        }
        for (Map<String, Object> e : rules) {
            knowledgeMapper.insertStockChanKnowledge(toRow(e, "rule"));
            n++;
        }
        if (n > 0) {
            log.info("已将 {} 条缠论知识JSON数据迁移入数据库", n);
        }
    }

    private StockChanKnowledge toRow(Map<String, Object> e, String kind) {
        StockChanKnowledge k = new StockChanKnowledge();
        k.setKind(kind);
        k.setName(str(e.get("name")));
        k.setAliases(strList(e.get("aliases")));
        k.setRelated(strList(e.get("related")));
        k.setTags(strList(e.get("tags")));
        if ("rule".equals(kind)) {
            k.setCategory(str(e.get("pattern")));
            k.setDefinition(str(e.get("condition")));
            k.setJudgement(str(e.get("action")));
            k.setRisk(str(e.get("risk")));
        } else {
            k.setCategory(str(e.get("category")));
            k.setDefinition(str(e.get("definition")));
            k.setJudgement(str(e.get("judgement")));
            k.setRules(strList(e.get("rules")));
        }
        k.setStatus("0");
        k.setCreateBy("system");
        return k;
    }

    private Map<String, Object> convertToMap(StockChanKnowledge k) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", k.getName());
        m.put("aliases", k.getAliases() == null ? new ArrayList<String>() : k.getAliases());
        m.put("related", k.getRelated() == null ? new ArrayList<String>() : k.getRelated());
        m.put("tags", k.getTags() == null ? new ArrayList<String>() : k.getTags());
        if ("rule".equals(k.getKind())) {
            m.put("pattern", k.getCategory());
            m.put("condition", k.getDefinition());
            m.put("action", k.getJudgement());
            m.put("risk", k.getRisk());
            m.put("rules", new ArrayList<String>());
        } else {
            m.put("category", k.getCategory());
            m.put("definition", k.getDefinition());
            m.put("judgement", k.getJudgement());
            m.put("rules", k.getRules() == null ? new ArrayList<String>() : k.getRules());
        }
        return m;
    }

    private List<Map<String, Object>> load(String path) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {
            });
        }
    }

    private List<Map<String, Object>> allEntries() {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<String, Object> e : concepts) {
            e.put("kind", "concept");
            entries.add(e);
        }
        for (Map<String, Object> e : rules) {
            e.put("kind", "rule");
            entries.add(e);
        }
        return entries;
    }

    // ------------------------------------------------------------------
    // 检索打分（中文子串匹配）
    // ------------------------------------------------------------------

    private List<String> splitQuery(String query) {
        List<String> parts = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return parts;
        }
        String[] arr = query.trim().split("[\\s,，、;；。.]+");
        for (String p : arr) {
            if (!p.isEmpty()) {
                parts.add(p);
            }
        }
        return parts;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static List<String> strList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List) {
            for (Object v : (List<?>) o) {
                out.add(str(v));
            }
        }
        return out;
    }

    private static String entryText(Map<String, Object> e) {
        StringBuilder sb = new StringBuilder();
        sb.append(str(e.get("name"))).append(' ');
        for (String a : strList(e.get("aliases"))) {
            sb.append(a).append(' ');
        }
        sb.append(str(e.get("definition"))).append(' ');
        for (String r : strList(e.get("rules"))) {
            sb.append(r).append(' ');
        }
        sb.append(str(e.get("judgement"))).append(' ');
        sb.append(str(e.get("condition"))).append(' ');
        sb.append(str(e.get("pattern"))).append(' ');
        sb.append(str(e.get("action"))).append(' ');
        for (String t : strList(e.get("tags"))) {
            sb.append(t).append(' ');
        }
        for (String r : strList(e.get("related"))) {
            sb.append(r).append(' ');
        }
        return sb.toString();
    }

    private static int countOccurrences(String text, String kw) {
        if (kw.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(kw, idx)) >= 0) {
            count++;
            idx += kw.length();
        }
        return count;
    }

    private static double scoreEntry(Map<String, Object> e, List<String> keywords) {
        String name = str(e.get("name"));
        List<String> aliases = strList(e.get("aliases"));
        String text = entryText(e);
        double score = 0.0;
        for (String kw : keywords) {
            String kl = kw.toLowerCase();
            if (kl.equals(name.toLowerCase())) {
                score += 100;
            }
            for (String a : aliases) {
                if (kl.equals(a.toLowerCase())) {
                    score += 90;
                }
            }
            if (name.toLowerCase().contains(kl)) {
                score += 50;
            }
            for (String a : aliases) {
                if (a.toLowerCase().contains(kl)) {
                    score += 40;
                }
            }
            score += countOccurrences(text.toLowerCase(), kl) * 8;
        }
        return score;
    }

    public List<ChanKnowledgeEntry> search(String query, int topK) {
        List<String> keywords = splitQuery(query);
        if (keywords.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> entries = allEntries();
        List<Object[]> scored = new ArrayList<>();
        for (Map<String, Object> e : entries) {
            double s = scoreEntry(e, keywords);
            if (s > 0) {
                scored.add(new Object[]{s, e});
            }
        }
        scored.sort((a, b) -> Double.compare((Double) b[0], (Double) a[0]));

        List<ChanKnowledgeEntry> out = new ArrayList<>();
        int limit = Math.min(topK, scored.size());
        for (int i = 0; i < limit; i++) {
            Map<String, Object> e = (Map<String, Object>) scored.get(i)[1];
            ChanKnowledgeEntry ce = new ChanKnowledgeEntry();
            ce.setKind(str(e.get("kind")));
            ce.setName(str(e.get("name")));
            ce.setCategory(e.containsKey("category") ? str(e.get("category")) : str(e.get("pattern")));
            ce.setDefinition(e.containsKey("definition") ? str(e.get("definition")) : str(e.get("condition")));
            ce.setRules(strList(e.get("rules")));
            ce.setJudgement(e.containsKey("judgement") ? str(e.get("judgement")) : str(e.get("action")));
            ce.setRelated(strList(e.get("related")));
            ce.setTags(strList(e.get("tags")));
            ce.setScore(Math.round((Double) scored.get(i)[0] * 10.0) / 10.0);
            out.add(ce);
        }
        return out;
    }

    public List<ChanKnowledgeEntry> search(String query) {
        return search(query, 8);
    }

    // ------------------------------------------------------------------
    // 结合分析结果给出知识解释
    // ------------------------------------------------------------------

    public List<ChanKnowledgeEntry> suggestForReport(Chan2Result report) {
        List<String> queries = new ArrayList<>();
        if ("中枢震荡".equals(report.getState())) {
            queries.add("中枢震荡");
            queries.add("中枢顶");
            queries.add("中枢底");
            queries.add("高抛低吸");
        } else {
            queries.add("趋势");
            if ("向上".equals(report.getTrendDirection())) {
                queries.add("三类买卖点");
                queries.add("顶背驰");
                queries.add("真假突破");
            } else {
                queries.add("三类买卖点");
                queries.add("底背驰");
                queries.add("真假突破");
            }
        }
        if (Boolean.TRUE.equals(report.getDivergenceTop())) {
            queries.add("顶背驰");
            queries.add("第一类卖点");
        }
        if (Boolean.TRUE.equals(report.getDivergenceBottom())) {
            queries.add("底背驰");
            queries.add("第一类买点");
        }
        if (report.getPivotLevel() != null) {
            queries.add("级别");
        }

        Set<String> seen = new LinkedHashSet<>();
        List<ChanKnowledgeEntry> out = new ArrayList<>();
        for (String q : queries) {
            for (ChanKnowledgeEntry r : search(q, 3)) {
                if (!seen.contains(r.getName())) {
                    seen.add(r.getName());
                    out.add(r);
                }
            }
        }
        return out;
    }

    public String formatResults(List<ChanKnowledgeEntry> results) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (ChanKnowledgeEntry r : results) {
            String kind = "concept".equals(r.getKind()) ? "概念" : "规则";
            sb.append(i++).append(". [").append(kind).append("] ").append(r.getName())
                    .append("  (相关度 ").append(r.getScore() == null ? "-" : r.getScore()).append(")\n");
            if (r.getCategory() != null && !r.getCategory().isEmpty()) {
                sb.append("   分类: ").append(r.getCategory()).append('\n');
            }
            if (r.getDefinition() != null && !r.getDefinition().isEmpty()) {
                sb.append("   说明: ").append(r.getDefinition()).append('\n');
            }
            if (r.getRules() != null) {
                int cnt = Math.min(3, r.getRules().size());
                for (int k = 0; k < cnt; k++) {
                    sb.append("     - ").append(r.getRules().get(k)).append('\n');
                }
            }
            if (r.getJudgement() != null && !r.getJudgement().isEmpty()) {
                sb.append("   判断: ").append(r.getJudgement()).append('\n');
            }
        }
        return sb.toString();
    }
}
