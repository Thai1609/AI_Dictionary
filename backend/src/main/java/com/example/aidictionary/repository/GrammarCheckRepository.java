package com.example.aidictionary.repository;

import com.example.aidictionary.entity.GrammarCheck;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrammarCheckRepository extends JpaRepository<GrammarCheck, Long> {
    List<GrammarCheck> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
