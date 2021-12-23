package com.mozi.moziserver.repository;

import com.mozi.moziserver.model.entity.Declaration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeclarationRepository extends JpaRepository<Declaration, Long> {
}
