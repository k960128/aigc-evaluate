-- PGVector manual initialization script.
-- The app config uses rag.vector.pg.initialize-schema=false, so these DDL statements are not executed automatically.
-- The embedding dimension must match rag.vector.pg.dimensions and the enabled embedding_model_info.dimension.
-- Current L2 real recall plan uses dimension 1024 and table public.vector_store.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS public.vector_store (
  id UUID PRIMARY KEY,
  content TEXT,
  metadata JSON,
  embedding VECTOR(1024)
);

CREATE INDEX IF NOT EXISTS vector_store_embedding_hnsw_idx
  ON public.vector_store
  USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS vector_store_metadata_gin_idx
  ON public.vector_store
  USING gin ((metadata::jsonb));

CREATE INDEX IF NOT EXISTS vector_store_feature_id_idx
  ON public.vector_store ((metadata ->> 'featureId'));

CREATE INDEX IF NOT EXISTS vector_store_risk_details_id_idx
  ON public.vector_store ((metadata ->> 'riskDetailsId'));

CREATE INDEX IF NOT EXISTS vector_store_category_id_idx
  ON public.vector_store ((metadata ->> 'categoryId'));

CREATE INDEX IF NOT EXISTS vector_store_content_hash_idx
  ON public.vector_store ((metadata ->> 'contentHash'));
