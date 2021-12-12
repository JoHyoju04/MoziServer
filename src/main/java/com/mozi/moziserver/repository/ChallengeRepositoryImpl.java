package com.mozi.moziserver.repository;

import com.mozi.moziserver.model.entity.Challenge;
import com.mozi.moziserver.model.entity.QChallenge;
import com.mozi.moziserver.model.entity.QChallengeTag;
import com.mozi.moziserver.model.entity.QChallengeTheme;
import com.mozi.moziserver.model.mappedenum.ChallengeTagType;
import com.mozi.moziserver.model.mappedenum.ChallengeThemeType;
import com.querydsl.core.types.Predicate;


import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChallengeRepositoryImpl extends QuerydslRepositorySupport implements ChallengeRepositorySupport {
    private final QChallenge qChallenge = QChallenge.challenge;
    private final QChallengeTag qChallengeTag = QChallengeTag.challengeTag;
    private final QChallengeTheme qChallengeTheme = QChallengeTheme.challengeTheme;

    public ChallengeRepositoryImpl() {
        super(Challenge.class);
    }

    @Override
    public List<Challenge> findAll (
            Long userSeq,
            ChallengeTagType tagType,
            ChallengeThemeType themeType,
            Integer pageSize,
            Long prevLastPostSeq
    ) {
        final Predicate[] predicates = new Predicate[]{
                predicateOptional(qChallenge.seq::lt,prevLastPostSeq),
        };

        List<Challenge> challengeList = from(qChallenge)
                .where(predicates)
                .limit(pageSize)
                .fetch()
                .stream()
                .collect(Collectors.toList());

        challengeList.forEach(t -> t.setTagList(new LinkedList<>()));
        challengeList.forEach(t -> t.setThemeList(new LinkedList<>()));

        // OneToMany 모두 이 방식으로 변경
        from(qChallengeTag)
                .where(qChallengeTag.id.challenge.in(challengeList))
                .fetch()
                .forEach(tag -> tag.getId().getChallenge().getTagList().add(tag));

        from(qChallengeTheme)
                .where(qChallengeTheme.id.challenge.in(challengeList))
                .fetch()
                .forEach(theme -> theme.getId().getChallenge().getThemeList().add(theme));

        if(tagType != null) {
            return challengeList
                    .stream()
                    .filter( c -> c.getTagList().stream().anyMatch( ct -> ct.getId().getTagName() == tagType))
                    .collect(Collectors.toList());
        }
        else if(themeType != null) {
            return challengeList
                    .stream()
                    .filter(c -> c.getThemeList().stream().anyMatch(ct -> ct.getId().getThemeName() == themeType))
                    .collect(Collectors.toList());
        }

        return challengeList;
    }

    private <T> Predicate predicateOptional(final Function<T, Predicate> whereFunc, final T value) {
        return value != null ? whereFunc.apply(value) : null;
    }
}