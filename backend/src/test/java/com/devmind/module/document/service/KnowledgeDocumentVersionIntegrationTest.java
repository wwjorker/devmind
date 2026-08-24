package com.devmind.module.document.service;

import com.devmind.module.document.dto.CreateDocumentRequest;
import com.devmind.module.document.dto.UpdateDocumentRequest;
import com.devmind.module.document.entity.KnowledgeDocument;
import com.devmind.module.document.entity.KnowledgeDocumentVersion;
import com.devmind.module.document.mapper.KnowledgeDocumentMapper;
import com.devmind.module.search.service.ChunkVectorService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:document_version_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "devmind.ai.provider=mock"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KnowledgeDocumentVersionIntegrationTest {

    private static final Long USER_ID = 31L;

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeDocumentService documentService;
    private final KnowledgeDocumentVersionService versionService;
    private final KnowledgeDocumentMapper documentMapper;

    @MockBean
    private DocumentChunkService chunkService;

    @MockBean
    private ChunkVectorService chunkVectorService;

    @Autowired
    KnowledgeDocumentVersionIntegrationTest(DataSource dataSource,
                                            JdbcTemplate jdbcTemplate,
                                            KnowledgeDocumentService documentService,
                                            KnowledgeDocumentVersionService versionService,
                                            KnowledgeDocumentMapper documentMapper) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.documentService = documentService;
        this.versionService = versionService;
        this.documentMapper = documentMapper;
    }

    @BeforeAll
    void createPreVersionSchemaAndRunMigration() {
        jdbcTemplate.execute("""
                CREATE TABLE user_account (
                    id BIGINT PRIMARY KEY,
                    username VARCHAR(64) NOT NULL UNIQUE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE knowledge_document (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    title VARCHAR(120) NOT NULL,
                    content MEDIUMTEXT NOT NULL,
                    source_type VARCHAR(32) NOT NULL,
                    tags VARCHAR(255),
                    summary VARCHAR(500),
                    status TINYINT NOT NULL DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_version_test_document_user FOREIGN KEY (user_id)
                        REFERENCES user_account(id)
                )
                """);
        jdbcTemplate.update("INSERT INTO user_account (id, username) VALUES (?, ?)",
                USER_ID, "document-version-test");
        jdbcTemplate.update("""
                INSERT INTO knowledge_document
                    (id, user_id, title, content, source_type, tags, summary, status)
                VALUES (100, ?, 'legacy', 'legacy content', 'project_doc', 'legacy',
                        'before V9', 1)
                """, USER_ID);
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V9__version_knowledge_documents.sql")
        ).execute(dataSource);
    }

    @Test
    void shouldBackfillVersionOneForExistingDocuments() {
        KnowledgeDocument current = documentMapper.selectById(100L);
        KnowledgeDocumentVersion baseline = versionService.getOwnedVersion(USER_ID, 100L, 1);

        assertThat(current.getVersionNo()).isEqualTo(1);
        assertThat(baseline.getTitle()).isEqualTo("legacy");
        assertThat(baseline.getContent()).isEqualTo("legacy content");
        assertThat(baseline.getOrigin()).isEqualTo("MIGRATION_BASELINE");
        assertThat(baseline.getDocumentStatus()).isEqualTo(1);
    }

    @Test
    void shouldSnapshotMutationsAndRejectAStaleOptimisticWrite() {
        var created = documentService.create(USER_ID, createRequest());
        KnowledgeDocument stale = documentMapper.selectById(created.getId());

        var updated = documentService.update(USER_ID, created.getId(), updateRequest());
        stale.setContent("stale overwrite");
        int staleRows = documentMapper.updateById(stale);
        documentService.archive(USER_ID, created.getId());
        documentService.restore(USER_ID, created.getId());

        KnowledgeDocument current = documentMapper.selectById(created.getId());
        List<KnowledgeDocumentVersion> versions = versionService.listOwnedVersions(
                USER_ID, created.getId());
        assertThat(updated.getContent()).isEqualTo("version two");
        assertThat(staleRows).isZero();
        assertThat(current.getVersionNo()).isEqualTo(4);
        assertThat(current.getContent()).isEqualTo("version two");
        assertThat(current.getStatus()).isEqualTo(1);
        assertThat(versions)
                .extracting(KnowledgeDocumentVersion::getVersionNo)
                .containsExactly(4, 3, 2, 1);
        assertThat(versions)
                .extracting(KnowledgeDocumentVersion::getOrigin)
                .containsExactly(
                        DocumentVersionOrigin.USER_RESTORE.name(),
                        DocumentVersionOrigin.USER_ARCHIVE.name(),
                        DocumentVersionOrigin.USER_UPDATE.name(),
                        DocumentVersionOrigin.USER_CREATE.name());
        assertThat(versions.get(2).getContent()).isEqualTo("version two");
        assertThat(versions).allSatisfy(version -> {
            assertThat(version.getProposalId()).isNull();
            assertThat(version.getSourceEvidenceJson()).isNull();
        });
    }

    private CreateDocumentRequest createRequest() {
        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setTitle("versioned document");
        request.setContent("version one");
        request.setSourceType("project_doc");
        request.setTags("version");
        request.setSummary("initial");
        return request;
    }

    private UpdateDocumentRequest updateRequest() {
        UpdateDocumentRequest request = new UpdateDocumentRequest();
        request.setTitle("versioned document");
        request.setContent("version two");
        request.setSourceType("project_doc");
        request.setTags("version,updated");
        request.setSummary("updated");
        return request;
    }
}
