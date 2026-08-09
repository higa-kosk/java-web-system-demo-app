package com.example.demo.repository;

import com.example.demo.model.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long>{
    
    // タグ名（例："Java"）でデータベースを検索する
    Optional<Tag> findByName(String name);

    /**
     * 中間テーブル（bill_tags）での登場回数が多い順に、上位N件のタグを抜き出す
     * ネイティブSQL(MySQL専用のDATE_SUB)をやめ、DBの種類に依存しないJPQLで記述
     * 日時の下限(since)と件数上限(pageable)は呼び出し側から渡す
     */ 
    @Query("SELECT t FROM Tag t " +
            "LEFT JOIN t.bills b " +
            "WHERE b.createdAt >= :since " +
            "GROUP BY t.id " +
            "ORDER BY COUNT(b.id) DESC")
    List<Tag> findTrendingTags(@Param("since") LocalDateTime since);
}
