package com.kimgreen.backend.domain.community.repository;

import com.kimgreen.backend.domain.community.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("select p from Post p where member.memberId= :id and createdAt between :start and :end")
    public List<Post> findAllBetweenDate(@Param("id") Long memberId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    public List<Post> findAllByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
