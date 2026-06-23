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
}
