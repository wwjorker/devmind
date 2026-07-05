package com.devmind.module.ai.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalKeywordServiceTest {

    private final RetrievalKeywordService retrievalKeywordService = new RetrievalKeywordService(true);

    @Test
    void resolveKeywordsShouldExtractChineseAndEnglishTechnicalTerms() {
        List<String> keywords = retrievalKeywordService.resolveKeywords("Redis 缓存穿透和缓存雪崩有什么区别？");

        assertThat(keywords)
                .contains("Redis", "缓存穿透", "缓存雪崩")
                .hasSizeLessThanOrEqualTo(6);
    }

    @Test
    void resolveKeywordsShouldSupportJwtAndChineseAuthTerms() {
        List<String> keywords = retrievalKeywordService.resolveKeywords("如何解释 JWT 鉴权和登录流程？");

        assertThat(keywords).contains("JWT", "鉴权", "登录");
    }

    @Test
    void resolveKeywordsShouldFilterCommonEnglishQuestionWords() {
        List<String> keywords = retrievalKeywordService.resolveKeywords("How should I explain Redis cache penetration in an interview?");

        assertThat(keywords)
                .contains("Redis", "cache", "penetration")
                .doesNotContain("How", "should", "explain", "interview");
    }

    @Test
    void resolveKeywordsShouldDropPhraseEmbeddedInLongerWord() {
        List<String> keywords = retrievalKeywordService.resolveKeywords("Elasticsearch 倒排索引怎么做相关性排序？");

        // "索引" only appears inside "倒排索引"; keeping it would match every indexing note.
        assertThat(keywords)
                .contains("Elasticsearch")
                .doesNotContain("索引");
    }

    @Test
    void resolveKeywordsShouldKeepPhraseAfterConnectorCharacter() {
        List<String> keywords = retrievalKeywordService.resolveKeywords("如何解释 JWT 鉴权和登录流程？");

        // "登录" follows the connector "和", so it is a standalone term, not a substring.
        assertThat(keywords).contains("鉴权", "登录");
    }

    @Test
    void toLogKeywordShouldJoinResolvedKeywords() {
        String logKeyword = retrievalKeywordService.toLogKeyword(List.of("Redis", "缓存穿透", "缓存雪崩"));

        assertThat(logKeyword).isEqualTo("Redis,缓存穿透,缓存雪崩");
    }
}
