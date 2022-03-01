package com.mozi.moziserver.repository;

import com.mozi.moziserver.model.entity.*;
import com.mozi.moziserver.model.entity.QChallenge;
import com.mozi.moziserver.model.entity.QConfirm;
import com.mozi.moziserver.model.entity.QUser;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;



public class ConfirmRepositoryImpl extends QuerydslRepositorySupport implements ConfirmRepositorySupport{
    private final QConfirm qConfirm = QConfirm.confirm;
    private final QUser qUser= QUser.user;
    private final QChallenge qChallenge=QChallenge.challenge;
    @PersistenceContext
    private EntityManager entityManager;


    public ConfirmRepositoryImpl() {super(Confirm.class);}

    @Override
    public List<Confirm> findAllByOrderDesc() {

        List<User> userList=from(qUser)
                .fetch()
                .stream()
                .collect(Collectors.toList());

        List<Challenge> challengeList=from(qChallenge)
                .fetch()
                .stream()
                .collect(Collectors.toList());

        List<Confirm> confirmList = from(qConfirm)
                .innerJoin(qConfirm.challenge,qChallenge)
                .innerJoin(qConfirm.user,qUser)
                .where(qConfirm.user.in(userList),qConfirm.challenge.in(challengeList))
                .orderBy(qConfirm.createdAt.desc())
                .fetch()
                .stream()
                .collect(Collectors.toList());

        return confirmList;

    }

    @Override
    public List<Confirm> findAllList(Long prevLastConfirmSeq,Integer pageSize){
        List<User> userList=from(qUser)
                .fetch()
                .stream()
                .collect(Collectors.toList());

        List<Challenge> challengeList=from(qChallenge)
                .fetch()
                .stream()
                .collect(Collectors.toList());

        Predicate[] predicates = new Predicate[]{
                predicateOptional(qConfirm.seq::lt,prevLastConfirmSeq),
        };

        List<Confirm> confirmList = from(qConfirm)
                .innerJoin(qConfirm.challenge,qChallenge)
                .innerJoin(qConfirm.user,qUser)
                .where(qConfirm.user.in(userList),qConfirm.challenge.in(challengeList))
                .orderBy(qConfirm.createdAt.desc())
                .where(predicates)
                .limit(pageSize)
                .fetch()
                .stream()
                .collect(Collectors.toList());

        return confirmList;
    }

    @Override
    public List<Confirm> findByChallengeByOrderDesc(Long seq,Long prevLastConfirmSeq, Integer pageSize){
        List<User> userList=from(qUser)
                .fetch()
                .stream()
                .collect(Collectors.toList());

        List<Challenge> challenge=from(qChallenge)
                .where(qChallenge.seq.eq(seq))
                .fetch()
                .stream()
                .collect(Collectors.toList());

        Predicate[] predicates = new Predicate[]{
                predicateOptional(qConfirm.seq::lt,prevLastConfirmSeq),
        };

        List<Confirm> confirmList = from(qConfirm)
                .innerJoin(qConfirm.challenge,qChallenge)
                .innerJoin(qConfirm.user,qUser)
                .where(qConfirm.user.in(userList),qConfirm.challenge.in(challenge))
                .where(predicates)
                .orderBy(qConfirm.createdAt.desc())
                .limit(pageSize)
                .fetch()
                .stream()
                .collect(Collectors.toList());

        return confirmList;
    }

    @Override
    public List<Confirm> findByUserByOrderDesc(Long userSeq,Long prevLastConfirmSeq, Integer pageSize){
        List<User> user=from(qUser)
                .where(qUser.seq.eq(userSeq))
                .fetch()
                .stream()
                .collect(Collectors.toList());

        List<Challenge> challengeList=from(qChallenge)
                .fetch()
                .stream()
                .collect(Collectors.toList());

        Predicate[] predicates = new Predicate[]{
                predicateOptional(qConfirm.seq::lt,prevLastConfirmSeq),
        };

        List<Confirm> confirmList = from(qConfirm)
                .innerJoin(qConfirm.challenge,qChallenge)
                .innerJoin(qConfirm.user,qUser)
                .where(qConfirm.user.in(user),qConfirm.challenge.in(challengeList))
                .where(predicates)
                .orderBy(qConfirm.createdAt.desc())
                .limit(pageSize)
                .fetch()
                .stream()
                .collect(Collectors.toList());

        return confirmList;

    }

    @Override
    public Confirm findByUserAndSeq(Long userSeq,Long seq){
        List<User> user=from(qUser)
                .where(qUser.seq.eq(userSeq))
                .fetch()
                .stream()
                .collect(Collectors.toList());

        Confirm confirm = from(qConfirm)
                .where(qConfirm.user.in(user),qConfirm.seq.in(seq))
                .fetchOne();

        return confirm;
    }

    @Override
    public void updateDeclarationState(Confirm confirm,Byte state){
        update(qConfirm)
                .set(qConfirm.confirmState,state)
                .where(qConfirm.eq(confirm))
                .execute();

        entityManager.clear();
    }


    private <T> Predicate predicateOptional(final Function<T, Predicate> whereFunc, final T value) {
        return value != null ? whereFunc.apply(value) : null;
    }
}
