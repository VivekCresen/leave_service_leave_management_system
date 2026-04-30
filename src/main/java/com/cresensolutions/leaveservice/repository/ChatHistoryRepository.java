package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    Optional<ChatHistory> findByUserId(Long userId);

    Optional<ChatHistory> findByUserName(String userName);

    @Transactional
    @Modifying
    @Query(value = "UPDATE user_schema.chat_history " +
                   "SET conversations  = conversations || CAST(:sessionJson AS jsonb), " +
                   "    total_sessions = total_sessions + 1, " +
                   "    total_qa_pairs = total_qa_pairs + :qaPairCount, " +
                   "    updated_at     = NOW() " +
                   "WHERE user_id = :userId",
           nativeQuery = true)
    int appendSession(@Param("userId") Long userId,
                      @Param("sessionJson") String sessionJson,
                      @Param("qaPairCount") int qaPairCount);

  
    @Transactional
    @Modifying
    @Query(value = "UPDATE user_schema.chat_history " +
                   "SET conversations = ( " +
                   "    CASE " +
                   "        WHEN jsonb_array_length(conversations) = 1 THEN " +
                   "            jsonb_build_array( " +
                   "                jsonb_set( " +
                   "                    jsonb_set( " +
                   "                        conversations->0, " +
                   "                        '{qa_pairs}', " +
                   "                        (conversations->0->'qa_pairs') || CAST(:qaPairJson AS jsonb) " +
                   "                    ), " +
                   "                    '{total_qa_pairs}', " +
                   "                    to_jsonb(jsonb_array_length(conversations->0->'qa_pairs') + 1) " +
                   "                ) " +
                   "            ) " +
                   "        ELSE " +
                   "            ( " +
                   "                SELECT jsonb_agg(elem ORDER BY idx) " +
                   "                FROM jsonb_array_elements(conversations) WITH ORDINALITY AS t(elem, idx) " +
                   "                WHERE idx < jsonb_array_length(conversations) " +
                   "            ) " +
                   "            || " +
                   "            jsonb_build_array( " +
                   "                jsonb_set( " +
                   "                    jsonb_set( " +
                   "                        conversations -> (jsonb_array_length(conversations) - 1), " +
                   "                        '{qa_pairs}', " +
                   "                        (conversations -> (jsonb_array_length(conversations) - 1) -> 'qa_pairs') " +
                   "                            || CAST(:qaPairJson AS jsonb) " +
                   "                    ), " +
                   "                    '{total_qa_pairs}', " +
                   "                    to_jsonb(jsonb_array_length(conversations -> (jsonb_array_length(conversations) - 1) -> 'qa_pairs') + 1) " +
                   "                ) " +
                   "            ) " +
                   "    END " +
                   "), " +
                   "total_qa_pairs = total_qa_pairs + 1, " +
                   "updated_at     = NOW() " +
                   "WHERE user_id = :userId",
           nativeQuery = true)
    int appendQaPairToLastSession(@Param("userId") Long userId,
                                  @Param("qaPairJson") String qaPairJson);
}
