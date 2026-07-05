package com.devmind;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.module.document.dto.CreateDocumentRequest;
import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.document.mapper.DocumentChunkMapper;
import com.devmind.module.document.service.KnowledgeDocumentService;
import com.devmind.module.document.vo.DocumentResponse;
import com.devmind.module.search.dto.ChunkFullTextMatch;
import com.devmind.module.search.entity.DocumentChunkVector;
import com.devmind.module.search.mapper.DocumentChunkVectorMapper;
import com.devmind.module.search.strategy.RetrievalStrategy;
import com.devmind.module.search.vo.ChunkSearchResponse;
import com.devmind.module.user.entity.UserAccount;
import com.devmind.module.user.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "devmind.ai.provider=mock",
        "devmind.redis.host=127.0.0.1",
        "devmind.redis.port=6390",
        "spring.flyway.clean-disabled=false"
})
class DevMindMySqlIntegrationTest {

    private static final String CACHE_PENETRATION_CN = "\u7f13\u5b58\u7a7f\u900f";
    private static final String DATABASE_CN = "\u6570\u636e\u5e93";
    private static final String REDIS_DOCUMENT_TITLE = "Redis \u7f13\u5b58\u7a7f\u900f\u96c6\u6210\u6d4b\u8bd5";
    private static final String REDIS_DOCUMENT_CONTENT_CN = """
            Redis \u7f13\u5b58\u7a7f\u900f\u662f\u6307\u5927\u91cf\u8bf7\u6c42\u67e5\u8be2\u4e0d\u5b58\u5728\u7684\u6570\u636e\uff0c\u7f13\u5b58\u65e0\u6cd5\u547d\u4e2d\uff0c\u8bf7\u6c42\u4f1a\u53cd\u590d\u6253\u5230\u6570\u636e\u5e93\u3002
            \u5e38\u89c1\u89e3\u51b3\u65b9\u6848\u5305\u62ec\u7f13\u5b58\u7a7a\u503c\u3001\u53c2\u6570\u6821\u9a8c\u3001\u63a5\u53e3\u9650\u6d41\u548c\u5e03\u9686\u8fc7\u6ee4\u5668\uff0c\u76ee\u6807\u662f\u4fdd\u62a4 MySQL\u3002
            """;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("devmind_integration")
            .withUsername("devmind")
            .withPassword("devmind");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    private final UserAccountMapper userAccountMapper;
    private final KnowledgeDocumentService documentService;
    private final DocumentChunkMapper chunkMapper;
    private final DocumentChunkVectorMapper vectorMapper;
    private final RetrievalStrategy retrievalStrategy;

    @Autowired
    DevMindMySqlIntegrationTest(UserAccountMapper userAccountMapper,
                                KnowledgeDocumentService documentService,
                                DocumentChunkMapper chunkMapper,
                                DocumentChunkVectorMapper vectorMapper,
                                RetrievalStrategy retrievalStrategy) {
        this.userAccountMapper = userAccountMapper;
        this.documentService = documentService;
        this.chunkMapper = chunkMapper;
        this.vectorMapper = vectorMapper;
        this.retrievalStrategy = retrievalStrategy;
    }

    @Test
    void flywayMigratesRealMySqlAndRetrievalUsesChunksVectorsAndFullTextSql() {
        Long userId = createUser();
        DocumentResponse document = documentService.create(userId, createRedisDocument());

        List<DocumentChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getUserId, userId)
                .eq(DocumentChunk::getDocumentId, document.getId())
                .eq(DocumentChunk::getStatus, 1));
        assertThat(chunks).isNotEmpty();

        List<DocumentChunkVector> vectors = vectorMapper.selectList(new LambdaQueryWrapper<DocumentChunkVector>()
                .eq(DocumentChunkVector::getUserId, userId)
                .eq(DocumentChunkVector::getDocumentId, document.getId())
                .eq(DocumentChunkVector::getStatus, 1));
        assertThat(vectors)
                .hasSameSizeAs(chunks)
                .allSatisfy(vector -> assertThat(vector.getVectorJson()).contains("redis"));

        List<ChunkSearchResponse> retrieved = retrievalStrategy.retrieve(
                userId,
                List.of(CACHE_PENETRATION_CN, "Redis", DATABASE_CN),
                3
        );
        assertThat(retrieved)
                .isNotEmpty()
                .anySatisfy(result -> assertThat(result.getDocumentTitle()).isEqualTo(REDIS_DOCUMENT_TITLE));

        List<ChunkFullTextMatch> englishFullTextMatches = chunkMapper.searchActiveChunksByFullText(
                userId,
                "cache penetration",
                5
        );
        assertThat(englishFullTextMatches)
                .isNotEmpty()
                .allSatisfy(match -> assertThat(match.getContent()).containsIgnoringCase("cache penetration"));

        List<ChunkFullTextMatch> chineseFullTextMatches = chunkMapper.searchActiveChunksByFullText(
                userId,
                CACHE_PENETRATION_CN,
                5
        );
        assertThat(chineseFullTextMatches)
                .as("ngram-parsed FULLTEXT index should match Chinese terms directly")
                .isNotEmpty()
                .allSatisfy(match -> assertThat(match.getContent()).contains(CACHE_PENETRATION_CN));
    }

    private Long createUser() {
        UserAccount user = new UserAccount();
        user.setUsername("mysql_integration_user");
        user.setPasswordHash("$2a$10$integration-test-password-hash");
        user.setNickname("MySQL Integration User");
        user.setEmail("mysql-integration@example.com");
        user.setStatus(1);
        userAccountMapper.insert(user);
        return user.getId();
    }

    private CreateDocumentRequest createRedisDocument() {
        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setTitle(REDIS_DOCUMENT_TITLE);
        request.setSourceType("integration_test");
        request.setTags("Redis,cache,penetration," + DATABASE_CN);
        request.setSummary("Covers MySQL Flyway migrations, chunk creation, vector persistence, and retrieval.");
        request.setContent("""
                Redis cache penetration happens when repeated requests query data that does not exist.
                The cache cannot hit, so the request goes through Redis and reaches MySQL every time.

                """ + REDIS_DOCUMENT_CONTENT_CN);
        return request;
    }
}
