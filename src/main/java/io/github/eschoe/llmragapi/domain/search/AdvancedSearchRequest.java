package io.github.eschoe.llmragapi.domain.search;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class AdvancedSearchRequest {
    private String query;
    private SearchType searchType;
    private List<SearchFilter> filters;
    private SearchSort sort;
    private int page;
    private int size;
    private String sessionId;

    public AdvancedSearchRequest() {}

    public AdvancedSearchRequest(String query, SearchType searchType) {
        this.query = query;
        this.searchType = searchType;
        this.page = 0;
        this.size = 10;
    }

    // Getters and Setters
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public SearchType getSearchType() { return searchType; }
    public void setSearchType(SearchType searchType) { this.searchType = searchType; }

    public List<SearchFilter> getFilters() { return filters; }
    public void setFilters(List<SearchFilter> filters) { this.filters = filters; }

    public SearchSort getSort() { return sort; }
    public void setSort(SearchSort sort) { this.sort = sort; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public enum SearchType {
        SEMANTIC,       // 의미 검색
        KEYWORD,        // 키워드 검색
        HYBRID          // 하이브리드 검색
    }

    public static class SearchFilter {
        private String field;
        private FilterOperator operator;
        private Object value;
        private Object value2; // 범위 검색용

        public SearchFilter() {}

        public SearchFilter(String field, FilterOperator operator, Object value) {
            this.field = field;
            this.operator = operator;
            this.value = value;
        }

        // Getters and Setters
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public FilterOperator getOperator() { return operator; }
        public void setOperator(FilterOperator operator) { this.operator = operator; }

        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }

        public Object getValue2() { return value2; }
        public void setValue2(Object value2) { this.value2 = value2; }

        public enum FilterOperator {
            EQUALS,
            NOT_EQUALS,
            GREATER_THAN,
            LESS_THAN,
            BETWEEN,
            CONTAINS,
            IN
        }
    }

    public static class SearchSort {
        private String field;
        private SortDirection direction;

        public SearchSort() {}

        public SearchSort(String field, SortDirection direction) {
            this.field = field;
            this.direction = direction;
        }

        // Getters and Setters
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public SortDirection getDirection() { return direction; }
        public void setDirection(SortDirection direction) { this.direction = direction; }

        public enum SortDirection {
            ASC, DESC
        }
    }
}
