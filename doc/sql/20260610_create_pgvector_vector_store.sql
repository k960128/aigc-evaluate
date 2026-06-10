-- PGVector manual initialization script.
-- The app config uses rag.vector.pg.initialize-schema=false, so these DDL statements are not executed automatically.
-- The embedding dimension must match the enabled embedding_model_info.dimension; use 1536 when not configured.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS vector_store (
  id UUID PRIMARY KEY,
  content TEXT,
  metadata JSON,
  embedding VECTOR(1536)
);

CREATE INDEX IF NOT EXISTS vector_store_embedding_hnsw_idx
  ON vector_store
  USING hnsw (embedding vector_cosine_ops);
