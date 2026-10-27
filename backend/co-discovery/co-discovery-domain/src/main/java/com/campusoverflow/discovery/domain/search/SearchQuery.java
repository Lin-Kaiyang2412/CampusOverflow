package com.campusoverflow.discovery.domain.search;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.page.PageRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public record SearchQuery(String keyword, Long courseId, String tag, SearchSort sort, boolean unansweredOnly,
                          boolean bountyOnly, PageRequest page) {

    private static final int MAX_TERMS = 5;

    public SearchQuery {
        keyword = keyword == null ? null : keyword.replaceAll("[\"+\\-<>()~*@]", " ").strip();
        if (keyword != null && keyword.isEmpty()) {
            keyword = null;
        }
        if (keyword != null && keyword.codePointCount(0, keyword.length()) > 50) {
            throw new BusinessRuleException("DIS-1002", "关键词不能超过50个字符");
        }
        if (keyword != null && terms(keyword).stream().anyMatch(t -> t.codePointCount(0, t.length()) < 2)) {
            throw new BusinessRuleException("DIS-1001", "每个关键词至少2个字符");
        }
        tag = tag == null || tag.isBlank() ? null : tag.strip().toLowerCase(Locale.ROOT);
        if (sort == null) {
            sort = keyword != null ? SearchSort.RELEVANCE : SearchSort.NEWEST;
        }
        if (sort == SearchSort.RELEVANCE && keyword == null) {
            sort = SearchSort.NEWEST;
        }
        page = page == null ? new PageRequest(1, 20) : page;
    }

    public List<String> terms() {
        return terms(keyword);
    }

    private static List<String> terms(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return Arrays.stream(keyword.strip().split("\\s+"))
                .filter(term -> !term.isBlank())
                .limit(MAX_TERMS)
                .toList();
    }
}
