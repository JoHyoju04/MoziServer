package com.mozi.moziserver.repository;

import com.mozi.moziserver.model.entity.ChallengeScrap;
import com.mozi.moziserver.model.entity.ChallengeUserSeq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChallengeScrapRepository extends JpaRepository<ChallengeScrap, Long>, ChallengeScrapRepositorySupport {
    @Modifying
    @Query(value = "DELETE FROM challenge_scrap WHERE user_seq = :userSeq AND challenge_seq = :challengeSeq", nativeQuery = true)
    int deleteChallengeScrapByUserSeqAndChallengeSeq(@Param("userSeq") Long userSeq,@Param("challengeSeq") Long challengeSeq);
}
