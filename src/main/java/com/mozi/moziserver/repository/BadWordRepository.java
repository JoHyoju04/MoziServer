package com.mozi.moziserver.repository;

import com.mozi.moziserver.model.entity.BadWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BadWordRepository extends JpaRepository<BadWord, Long> {

}
