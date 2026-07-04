package com.example.aidictionary.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aidictionary.entity.GrammarCheck;

public interface GrammarCheckRepository extends JpaRepository<GrammarCheck, Long> {
}