package com.devmind;

import com.devmind.module.ai.llm.DeepSeekLlmClient;
import com.devmind.module.ai.llm.LlmClient;
import com.devmind.module.ai.llm.MockLlmClient;
import com.devmind.module.search.embedding.EmbeddingClient;
import com.devmind.module.search.embedding.LocalEmbeddingClient;
import com.devmind.module.search.strategy.HybridRetrievalStrategy;
import com.devmind.module.search.strategy.RetrievalStrategy;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:devmind_context_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "devmind.ai.provider=mock"
})
class DevMindApplicationContextTest {

    private final ListableBeanFactory beanFactory;
    private final EmbeddingClient embeddingClient;
    private final RetrievalStrategy retrievalStrategy;
    private final Map<String, LlmClient> llmClients;

    @Autowired
    DevMindApplicationContextTest(
            ListableBeanFactory beanFactory,
            EmbeddingClient embeddingClient,
            RetrievalStrategy retrievalStrategy,
            Map<String, LlmClient> llmClients
    ) {
        this.beanFactory = beanFactory;
        this.embeddingClient = embeddingClient;
        this.retrievalStrategy = retrievalStrategy;
        this.llmClients = llmClients;
    }

    @Test
    void contextWiresDomainInterfacesToConcreteComponentsInsteadOfMapperProxies() {
        assertThat(embeddingClient).isInstanceOf(LocalEmbeddingClient.class);
        assertThat(retrievalStrategy).isInstanceOf(HybridRetrievalStrategy.class);
        assertThat(llmClients)
                .containsEntry("mockLlmClient", beanFactory.getBean(MockLlmClient.class))
                .containsEntry("deepSeekLlmClient", beanFactory.getBean(DeepSeekLlmClient.class));

        assertThat(AopUtils.isJdkDynamicProxy(embeddingClient)).isFalse();
        assertThat(AopUtils.isJdkDynamicProxy(retrievalStrategy)).isFalse();
        assertThat(llmClients.values()).noneMatch(AopUtils::isJdkDynamicProxy);
    }

    @Test
    void mapperFactoryBeansDoNotIncludeDomainServiceInterfaces() {
        assertThat(beanFactory.getBean("&userAccountMapper")).isInstanceOf(MapperFactoryBean.class);
        assertThat(beanFactory.containsBean("&embeddingClient")).isFalse();
        assertThat(beanFactory.containsBean("&retrievalStrategy")).isFalse();
        assertThat(beanFactory.containsBean("&llmClient")).isFalse();
    }
}
