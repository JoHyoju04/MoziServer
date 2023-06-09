package com.mozi.moziserver.service;

import com.mozi.moziserver.common.JpaUtil;
import com.mozi.moziserver.httpException.ResponseError;
import com.mozi.moziserver.model.entity.*;
import com.mozi.moziserver.model.mappedenum.DeclarationType;
import com.mozi.moziserver.model.mappedenum.PointReasonType;
import com.mozi.moziserver.model.req.ReqConfirmOfUser;
import com.mozi.moziserver.model.req.ReqConfirmSticker;
import com.mozi.moziserver.model.req.ReqList;
import com.mozi.moziserver.model.req.ReqUserStickerList;
import com.mozi.moziserver.model.res.ResWeekConfirm;
import com.mozi.moziserver.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmService {
    private final UserService userService;
    private final UserRepository userRepository;

    private final ChallengeRepository challengeRepository;

    private final ConfirmRepository confirmRepository;

    private final DeclarationRepository declarationRepository;

    private final ConfirmStickerRepository confirmStickerRepository;

    private final UserStickerRepository userStickerRepository;

    private final StickerRepository stickerRepository;

    private final S3ImageService s3ImageService;

    private final UserChallengeService userChallengeService;

    private final PlatformTransactionManager transactionManager;

    private final ChallengeRecordRepository challengeRecordRepository;

    private final UserRewardService userRewardService;
    private final ConfirmLikeRepository confirmLikeRepository;

    //인증 생성
    public void createConfirm(Long userSeq, Long challengeSeq, MultipartFile image) {

        User user = userRepository.findById(userSeq)
                .orElseThrow(ResponseError.NotFound.USER_NOT_EXISTS::getResponseException);

        Challenge challenge = challengeRepository.findById(challengeSeq)
                .orElseThrow(ResponseError.BadRequest.INVALID_SEQ::getResponseException);

        //신고안됨
        Byte state = 0;

        String imgUrl = null;
        try {
            imgUrl = s3ImageService.uploadFile(image, "confirm");
        } catch (Exception e) {
            throw new RuntimeException(e.getCause());
        }

        Confirm confirm = Confirm.builder()
                .user(user)
                .challenge(challenge)
                .imgUrl(imgUrl)
                .declarationState(state)
                .build();

        withTransaction(() -> {
            UserChallenge curUserChallenge = userChallengeService.getActiveUserChallenge(userSeq, challenge)
                    .orElseThrow(ResponseError.NotFound.USER_CHALLENGE_NOT_EXISTS::getResponseException);

            try {
                confirmRepository.save(confirm);
            } catch (Exception e) {
                throw ResponseError.BadRequest.ALREADY_CREATED.getResponseException(); // for duplicate exception
            }

            userChallengeService.updateUserChallengeResultComplete(curUserChallenge,LocalDate.now());

            ChallengeRecord challengeRecord = challengeRecordRepository.findByChallenge(challenge);

            challengeRecord.setTotalPlayerConfirmCnt(challengeRecord.getTotalPlayerConfirmCnt() + 1);
            challengeRecordRepository.save(challengeRecord);

            userRewardService.createUserPointRecord(user, PointReasonType.CHALLENGE_CONFIRM,challenge.getPoint());

        });
    }

    public List<Confirm> getConfirmList(Long userSeq, ReqList req) {
        List<Confirm> confirmList = confirmRepository.findAll(req.getPrevLastSeq(), req.getPageSize());

        Collections.shuffle(confirmList);

        setConfirmLike(userSeq, confirmList);

        setConfirmDeclaration(userSeq, confirmList);

        return confirmList.stream()
                .filter(c -> !c.isDeclared())
                .collect(Collectors.toList());
    }

    // 챌린지별 인증 조회
    public List<Confirm> getConfirmListByChallenge(Long userSeq, Long challengeSeq, ReqList req) {
        final Challenge challenge = challengeRepository.findById(challengeSeq)
                .orElseThrow(ResponseError.NotFound.CHALLENGE_NOT_EXISTS::getResponseException);

        List<Confirm> confirmList = confirmRepository.findAllByChallenge(challenge, req.getPrevLastSeq(), req.getPageSize());

        setConfirmLike(userSeq, confirmList);

        setConfirmDeclaration(userSeq, confirmList);

        return confirmList.stream()
                .filter(c -> !c.isDeclared())
                .collect(Collectors.toList());
    }

    public List<Confirm> getConfirmListByUserChallenge(Long userSeq, Long userChallengeSeq) {
        final UserChallenge userChallenge = userChallengeService.getUserChallenge(userChallengeSeq);

        List<Confirm> confirmList = confirmRepository.findByUserAndPeriod(
                userChallenge.getUser(),
                userChallenge.getChallenge(),
                userChallenge.getStartDate().atTime(0,0),
                userChallenge.getEndDate().plusDays(1).atTime(0,0)
        );

        setConfirmLike(userSeq, confirmList);

        setConfirmDeclaration(userSeq, confirmList);

        return confirmList.stream()
                .filter(c -> !c.isDeclared())
                .collect(Collectors.toList());
    }

    public Optional<Confirm> getConfirmByChallenge(Challenge challenge) {
        return confirmRepository.findByChallenge(challenge);
    }

    private void setConfirmLike(Long userSeq, List<Confirm> confirmList) {
        User user = userService.getUserBySeq(userSeq)
                .orElseThrow(ResponseError.InternalServerError.UNEXPECTED_ERROR::getResponseException);

        List<ConfirmLike> confirmLikeList = confirmLikeRepository.findAllByUserAndConfirmsIn(user, confirmList);
        HashSet<Long> likedConfirmSeqSet = new HashSet<>(confirmLikeList.stream()
                .map(confirmLike -> confirmLike.getConfirm().getSeq())
                .collect(Collectors.toSet()));

        for (Confirm confirm: confirmList) {
            boolean isLiked  = likedConfirmSeqSet.contains(confirm.getSeq());
            confirm.setLiked(isLiked);
        }

        //return confirmList;
    }

    private void setConfirmDeclaration(Long userSeq, List<Confirm> confirmList) {
        User user = userService.getUserBySeq(userSeq)
                .orElseThrow(ResponseError.InternalServerError.UNEXPECTED_ERROR::getResponseException);

        List<Declaration> declarationList = declarationRepository.findByUser(user);
        HashSet<Long> declarationConfirmSeqSet = new HashSet<>(declarationList.stream()
                .map( declaration -> declaration.getConfirm().getSeq())
                .collect(Collectors.toSet()));

        for (Confirm confirm: confirmList) {
            boolean isDeclared  = declarationConfirmSeqSet.contains(confirm.getSeq());
            confirm.setDeclared(isDeclared);
        }

    }

    @Transactional
    public List<Confirm> getUserConfirmList(Long userSeq, ReqList req) {
        List<Confirm> confirmList = confirmRepository.findByUserByOrderDesc(userSeq, req.getPrevLastSeq(), req.getPageSize());

        setConfirmLike(userSeq, confirmList);

        return confirmList;
    }

    //인증 하나 조회
    @Transactional
    public Confirm getConfirm(Long confirmSeq) {
        return confirmRepository.findBySeq(confirmSeq);
    }

    //confirmSticker 조회
    @Transactional
    public List<ConfirmSticker> getConfirmStickerList(Long seq) {
        return confirmStickerRepository.findAllBySeq(seq);
    }

    @Transactional
    public void deleteConfirm(Long userSeq, Long confirmSeq) {
        User user = userRepository.findById(userSeq)
                .orElseThrow(ResponseError.NotFound.USER_NOT_EXISTS::getResponseException);

        Confirm confirm = getConfirm(confirmSeq);

        if (!confirm.getUser().equals(user)) throw ResponseError.BadRequest.INVALID_USER.getResponseException();

        try {
            int deleteCount = confirmRepository.deleteConfirm(confirmSeq);
            if (deleteCount == 0) {
                // 동시성 처리: 지울려고 했는데 못 지웠으면 함수실행을 끝낸다.
                throw ResponseError.BadRequest.ALREADY_DELETED.getResponseException();
            }
        } catch (Exception e) {
            throw ResponseError.BadRequest.ALREADY_DELETED.getResponseException(); // for duplicate exception
        } // FIXME DuplicateKeyException
    }

    //신고 생성
    @Transactional
    public void createDeclaration(Long userSeq, Long confirmSeq, DeclarationType type) {

        User user = userRepository.findById(userSeq)
                .orElseThrow(ResponseError.NotFound.USER_NOT_EXISTS::getResponseException);

       Confirm confirm = confirmRepository.findBySeq(confirmSeq);
       if(confirm == null){
           throw ResponseError.NotFound.CONFIRM_NOT_EXISTS.getResponseException();
       }

       if(user.equals(confirm.getUser())){
           throw ResponseError.BadRequest.INVALID_CONFIRM.getResponseException();
       }

       Declaration declaration=declarationRepository.findByConfirmAndUser(confirm,user);
       if(declaration != null){
           throw ResponseError.BadRequest.ALREADY_CREATED.getResponseException();
       }

        Byte state=1;
        confirmRepository.updateDeclarationCnt(confirm,state,confirm.getDeclarationCnt()+1);

        declaration = Declaration.builder()
                .confirm(confirm)
                .user(user)
                .declarationType(type)
                .build();

        try {
            declarationRepository.save(declaration);
        } catch (Exception e) {
            throw ResponseError.BadRequest.ALREADY_CREATED.getResponseException(); // for duplicate exception
        }

    }

    @Transactional
    public List<Sticker> getStickerList(List<Long> stickerSeqList) {
        return stickerRepository.findAllBySeq(stickerSeqList);
    }

    @Transactional
    public List<Sticker> getSticker() {
        List<Sticker> stickerList = stickerRepository.findAll();

        return stickerList;
    }

    //UserSticker 생성
    @Transactional
    public void createUserSticker(Long userSeq, ReqUserStickerList userStickerList) {

        User user = userRepository.findById(userSeq)
                .orElseThrow(ResponseError.NotFound.USER_NOT_EXISTS::getResponseException);

        List<Long> stickerSeqList = userStickerList.getStickerSeqList();

        List<UserSticker> newUserStickerList = new ArrayList<UserSticker>();


        List<Sticker> stickerList = getStickerList(stickerSeqList);
        for (Sticker sticker : stickerList) {
            UserStickerId userStickerId = new UserStickerId(user, sticker);
            UserSticker userSticker = UserSticker.builder()
                    .id(userStickerId)
                    .build();
            newUserStickerList.add(userSticker);


        }
        try {
            userStickerRepository.saveAll(newUserStickerList);
        } catch (Exception e) {
            throw ResponseError.BadRequest.ALREADY_CREATED.getResponseException(); // for duplicate exception
        }

    }

    //ConfirmSticker 생성
    @Transactional
    public void createConfirmSticker(Long userSeq, Long confirmSeq, ReqConfirmSticker reqConfirmSticker) {

        User user = userRepository.findById(userSeq)
                .orElseThrow(ResponseError.NotFound.USER_NOT_EXISTS::getResponseException);

        Confirm confirm = confirmRepository.findBySeq(confirmSeq);

        //confirmSticker는 자기 자신에 스토리에 스티커를 붙이지 못한다.
        if (userSeq == confirm.getUser().getSeq()) throw ResponseError.BadRequest.INVALID_USER.getResponseException();

        //true면 존재한다.
        Boolean createdCheck = confirmStickerRepository.findByUserAndConfirmSeq(userSeq, confirmSeq);

        //하나만 붙일수있다. confirmSeq가 같고 userSeq가 같으면 못붙인다.
        if (createdCheck == true) throw ResponseError.BadRequest.ALREADY_CREATED.getResponseException();

        Optional<Sticker> sticker = stickerRepository.findById(reqConfirmSticker.getStickerSeq());

        UserStickerId userStickerId = new UserStickerId(user, sticker.get());

        UserSticker userSticker = userStickerRepository.findById(userStickerId)
                .orElseThrow(ResponseError.NotFound.USER_STICKER_NOT_EXISTS::getResponseException);

        ConfirmSticker confirmSticker = ConfirmSticker.builder()
                .confirm(confirm)
                .user(user)
                .sticker(sticker.get())
                .locationX(reqConfirmSticker.getLocationX())
                .locationY(reqConfirmSticker.getLocationY())
                .angle(reqConfirmSticker.getAngle())
                .inch(reqConfirmSticker.getInch())
                .build();

        try {
            confirmStickerRepository.save(confirmSticker);
        } catch (Exception e) {
            throw ResponseError.BadRequest.ALREADY_CREATED.getResponseException(); // for duplicate exception
        }

    }

    public void createConfirmLike(Long userSeq, Long confirmSeq) {
        User user = userRepository.findById(userSeq)
                .orElseThrow(ResponseError.NotFound.USER_NOT_EXISTS::getResponseException);

        Confirm confirm = confirmRepository.findBySeq(confirmSeq);
        if (confirm == null) {
            throw ResponseError.NotFound.CONFIRM_NOT_EXISTS.getResponseException();
        }

        ConfirmLike confirmLike = ConfirmLike.builder()
                .confirm(confirm)
                .user(user)
                .build();

        try {
            confirmLikeRepository.save(confirmLike);
        } catch (DataIntegrityViolationException e) {
            if (JpaUtil.isDuplicateKeyException(e)) {
                throw ResponseError.BadRequest.ALREADY_EXISTS_CONFIRM_LIKE.getResponseException();
            }
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        } catch (Exception e) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }

        confirmRepository.incrementLikeCnt(confirm.getSeq());
    }

    @Transactional
    public void deleteConfirmLike(Long userSeq, Long confirmSeq) {
        User user = userRepository.findById(userSeq)
                .orElseThrow(ResponseError.NotFound.USER_NOT_EXISTS::getResponseException);

        Confirm confirm = confirmRepository.findBySeq(confirmSeq);
        if (confirm == null) {
            throw ResponseError.NotFound.CONFIRM_NOT_EXISTS.getResponseException();
        }

        try {
            int deleteCnt = confirmLikeRepository.deleteByConfirmSeqAndUserSeq(confirm.getSeq(), user.getSeq());
            if (deleteCnt != 1) {
                throw ResponseError.NotFound.CONFIRM_LIKE_NOT_EXISTS.getResponseException();
            }
        } catch (Exception e) {
            throw ResponseError.NotFound.CONFIRM_LIKE_NOT_EXISTS.getResponseException();
        }

        confirmRepository.decrementLikeCnt(confirm.getSeq());
    }

    public ResWeekConfirm getWeekConfirm(){
        LocalDateTime today = LocalDateTime.now();

        List<Confirm> confirmList=confirmRepository.findByCreatedAt(today);

        List<User> userList=confirmList.stream().map(Confirm::getUser).collect(Collectors.toList());

        userList=userList.stream().distinct().collect(Collectors.toList());

        return ResWeekConfirm.of(userList,confirmList);
    }

    public List<Confirm> getConfirmListAboutPeriod(Long userSeq, ReqConfirmOfUser req) {
        User user = userRepository.findById(userSeq)
                .orElseThrow(ResponseError.NotFound.USER_NOT_EXISTS::getResponseException);

        Challenge challenge = challengeRepository.findById(req.getChallengeSeq())
                .orElseThrow(ResponseError.BadRequest.INVALID_SEQ::getResponseException);

        List<Confirm> confirmList = confirmRepository.findByUserAndPeriod(
               user, challenge, req.getStartDate().atTime(0,0), req.getEndDate().plusDays(1).atTime(0,0));

        setConfirmLike(userSeq, confirmList);

        return confirmList;
    }

    private void withTransaction(Runnable runnable) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();

        TransactionStatus status = transactionManager.getTransaction(definition);
        try {
            runnable.run();
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
        }
    }
}
