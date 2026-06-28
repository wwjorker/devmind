package com.devmind.module.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devmind.module.document.entity.DocumentChunk;
import com.devmind.module.search.dto.ChunkFullTextMatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    @Select("""
            SELECT
                c.id,
                c.document_id,
                c.user_id,
                c.chunk_index,
                c.content,
                c.token_count,
                c.status,
                c.created_at,
                c.updated_at,
                MATCH(c.content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) AS full_text_score
            FROM knowledge_document_chunk c
            WHERE c.user_id = #{userId}
              AND c.status = 1
              AND MATCH(c.content) AGAINST(#{query} IN NATURAL LANGUAGE MODE)
            ORDER BY full_text_score DESC, c.updated_at DESC
            LIMIT #{limit}
            """)
    List<ChunkFullTextMatch> searchActiveChunksByFullText(@Param("userId") Long userId,
                                                          @Param("query") String query,
                                                          @Param("limit") int limit);
}
