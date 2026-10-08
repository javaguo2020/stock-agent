package com.ruoyi.system.chan;

import lombok.Data;

import java.util.List;

/**
 * 知识库条目（概念/规则）。对应 Python kb.py search() 的输出结构。
 */
@Data
public class ChanKnowledgeEntry {
    private String kind;          // concept / rule
    private String name;
    private String category;
    private String definition;
    private List<String> rules;
    private String judgement;
    private List<String> related;
    private List<String> tags;
    private Double score;
}
