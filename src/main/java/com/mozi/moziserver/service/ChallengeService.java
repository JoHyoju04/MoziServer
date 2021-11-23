package com.mozi.moziserver.service;

import com.mozi.moziserver.model.entity.Challenge;
import com.mozi.moziserver.model.res.ResChallenge;
import com.mozi.moziserver.repository.ChallengeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeService {
    private final ChallengeRepository challengeRepository;

    // 챌린지 하나 조회
    public Optional<Challenge> getChallenge(Long seq) {
        return challengeRepository.findById(seq);
    }

    // 챌린지 모두 조회
    public List<Challenge> getChallengeList() {
        return challengeRepository.findAll();
    }

}
