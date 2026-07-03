package com.devmind.module.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "devmind.ai")
public class AiProperties {

    private String provider = "mock";
    private String deepseekApiKey;
    private String deepseekBaseUrl = "https://api.deepseek.com";
    private String deepseekModel = "deepseek-v4-flash";
    private Double deepseekTemperature = 0.2;
    private EmbeddingProperties embedding = new EmbeddingProperties();
    private RerankProperties rerank = new RerankProperties();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getDeepseekApiKey() {
        return deepseekApiKey;
    }

    public void setDeepseekApiKey(String deepseekApiKey) {
        this.deepseekApiKey = deepseekApiKey;
    }

    public String getDeepseekBaseUrl() {
        return deepseekBaseUrl;
    }

    public void setDeepseekBaseUrl(String deepseekBaseUrl) {
        this.deepseekBaseUrl = deepseekBaseUrl;
    }

    public String getDeepseekModel() {
        return deepseekModel;
    }

    public void setDeepseekModel(String deepseekModel) {
        this.deepseekModel = deepseekModel;
    }

    public Double getDeepseekTemperature() {
        return deepseekTemperature;
    }

    public void setDeepseekTemperature(Double deepseekTemperature) {
        this.deepseekTemperature = deepseekTemperature;
    }

    public EmbeddingProperties getEmbedding() {
        return embedding;
    }

    public void setEmbedding(EmbeddingProperties embedding) {
        this.embedding = embedding;
    }

    public RerankProperties getRerank() {
        return rerank;
    }

    public void setRerank(RerankProperties rerank) {
        this.rerank = rerank;
    }

    public static class EmbeddingProperties {

        private String provider = "local-sparse-vector";
        private RemoteProperties remote = new RemoteProperties();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public RemoteProperties getRemote() {
            return remote;
        }

        public void setRemote(RemoteProperties remote) {
            this.remote = remote;
        }
    }

    public static class RemoteProperties {

        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private Integer dimension = 0;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Integer getDimension() {
            return dimension;
        }

        public void setDimension(Integer dimension) {
            this.dimension = dimension;
        }
    }

    public static class RerankProperties {

        private String provider = "none";
        private RerankRemoteProperties remote = new RerankRemoteProperties();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public RerankRemoteProperties getRemote() {
            return remote;
        }

        public void setRemote(RerankRemoteProperties remote) {
            this.remote = remote;
        }
    }

    public static class RerankRemoteProperties {

        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
