package com.mozi.moziserver.repository;

import com.mozi.moziserver.model.entity.Challenge;
import com.mozi.moziserver.model.mappedenum.ChallengeTagType;
import com.mozi.moziserver.model.mappedenum.ChallengeThemeType;
import com.mozi.moziserver.model.req.ReqChallengeList;

import java.util.List;

public interface ChallengeRepositorySupport {
    List<Challenge> findAll (
            Long userSeq,
            List<String> tagType,
            List<String> themeType,
            Integer pageSize,
            Long prevLastPostSeq
    );
}
