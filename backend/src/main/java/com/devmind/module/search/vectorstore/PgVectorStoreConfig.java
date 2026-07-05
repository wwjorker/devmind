package com.devmind.module.search.vectorstore;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires the pgvector serving index only when explicitly enabled, so the default
 * configuration keeps running on MySQL alone with zero extra infrastructure.
 *
 * <p>The Postgres pool is deliberately NOT exposed as a Spring {@code DataSource} bean:
 * Boot's primary DataSource auto-configuration backs off as soon as any user-defined
 * DataSource bean exists, which would silently unplug MySQL. The pool lives inside
 * {@link PgVectorStore} and is closed with it.</p>
 */
@Configuration
@EnableConfigurationProperties(VectorStoreProperties.class)
public class PgVectorStoreConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "devmind.vector-store.provider", havingValue = "pgvector")
    public PgVectorStore pgVectorStore(VectorStoreProperties properties) {
        VectorStoreProperties.Pgvector pgvector = properties.getPgvector();
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(pgvector.getUrl());
        dataSource.setUsername(pgvector.getUsername());
        dataSource.setPassword(pgvector.getPassword());
        dataSource.setMaximumPoolSize(4);
        dataSource.setPoolName("devmind-pgvector");

        PgVectorStore store = new PgVectorStore(
                new JdbcTemplate(dataSource),
                pgvector.getDimension(),
                dataSource
        );
        store.initSchema();
        return store;
    }
}
