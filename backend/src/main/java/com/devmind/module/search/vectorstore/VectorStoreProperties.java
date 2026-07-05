package com.devmind.module.search.vectorstore;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devmind.vector-store")
public class VectorStoreProperties {

    public static final String PROVIDER_MYSQL_JSON = "mysql-json";
    public static final String PROVIDER_PGVECTOR = "pgvector";

    /**
     * mysql-json (default): dense vectors stay as JSON rows in MySQL and are compared
     * by brute-force cosine in the JVM. pgvector: dense vectors are double-written to
     * Postgres and served by an HNSW index.
     */
    private String provider = PROVIDER_MYSQL_JSON;

    private Pgvector pgvector = new Pgvector();

    public boolean isPgvectorEnabled() {
        return PROVIDER_PGVECTOR.equalsIgnoreCase(provider);
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Pgvector getPgvector() {
        return pgvector;
    }

    public void setPgvector(Pgvector pgvector) {
        this.pgvector = pgvector;
    }

    public static class Pgvector {

        private String url;
        private String username;
        private String password;
        private int dimension = 1024;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            this.dimension = dimension;
        }
    }
}
