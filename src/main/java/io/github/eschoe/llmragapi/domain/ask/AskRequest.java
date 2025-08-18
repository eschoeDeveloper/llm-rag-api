package io.github.eschoe.llmragapi.domain.ask;

public class AskRequest {

    private String query;
    private float[] embedding;

    public AskRequest() {}

    public AskRequest(String query, float[] embedding) {
        this.query = query;
        this.embedding = embedding;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

}
