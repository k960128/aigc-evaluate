CREATE TABLE "public"."vector_store" (
                                         "id" uuid NOT NULL DEFAULT uuid_generate_v4(),
                                         "content" text COLLATE "pg_catalog"."default",
                                         "metadata" json,
                                         "embedding" vector(1024),
                                         CONSTRAINT "vector_store_pkey" PRIMARY KEY ("id")
)
;

ALTER TABLE "public"."vector_store"
    OWNER TO "postgres";

CREATE INDEX "spring_ai_vector_index" ON "public"."vector_store" USING hnsw (
    "embedding" "public"."vector_cosine_ops"
    );