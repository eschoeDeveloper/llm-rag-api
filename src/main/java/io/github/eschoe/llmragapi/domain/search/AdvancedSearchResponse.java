package io.github.eschoe.llmragapi.domain.search;

import java.util.List;
import java.util.Map;

public class AdvancedSearchResponse {
    private List<SearchResult> results;
    private int page;
    private int size;
    private long totalElements;
    private String searchType;
    private Map<String, Object> metadata;

    public AdvancedSearchResponse() {}

    public AdvancedSearchResponse(List<SearchResult> results, int page, int size, long totalElements, String searchType) {
        this.results = results;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.searchType = searchType;
    }

    public AdvancedSearchResponse(List<SearchResult> results, int page, int size, long totalElements, String searchType, Map<String, Object> metadata) {
        this.results = results;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.searchType = searchType;
        this.metadata = metadata;
    }

    // Getters and Setters
    public List<SearchResult> getResults() { return results; }
    public void setResults(List<SearchResult> results) { this.results = results; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }

    public String getSearchType() { return searchType; }
    public void setSearchType(String searchType) { this.searchType = searchType; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public int getTotalPages() {
        return (int) Math.ceil((double) totalElements / size);
    }

    public boolean hasNext() {
        return page < getTotalPages() - 1;
    }

    public boolean hasPrevious() {
        return page > 0;
    }
}
