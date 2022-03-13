package com.mozi.moziserver.service;

import com.mozi.moziserver.common.Constant;
import com.mozi.moziserver.httpException.ResponseError;
import com.mozi.moziserver.model.entity.*;
import com.mozi.moziserver.model.mappedenum.DeclarationType;
import com.mozi.moziserver.model.mappedenum.UserChallengeResultType;
import com.mozi.moziserver.model.req.*;
import com.mozi.moziserver.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmService {
    private final UserRepository userRepository;

    private final ChallengeRepository challengeRepository;

    private final ConfirmRepository confirmRepository;

    private final DeclarationRepository declarationRepository;

    private final ConfirmStickerRepository confirmStickerRepository;

    private final UserStickerRepository userStickerRepository;

    private final StickerImgRepository stickerImgRepository;

    private final S3ImageService s3ImageService;

    private final UserChallengeService userChallengeService;

    //인증 생성
    @Transactional
    public void createConfirm(Long userSeq, Long challengeSeq, MultipartFile image){
        LocalDate today = LocalDate.now();

        User user = userRepository.findById(userSeq)
                .orElseThrow(ResponseError.NotFound.USER_NOT_EXISTS::getResponseException);

        Challenge challenge=challengeRepository.findById(challengeSeq)
                .orElseThrow(ResponseError.BadRequest.INVALID_SEQ::getResponseException);

        UserChallenge userChallenge = userChallengeService.getActiveUserChallenge(userSeq, challenge)
                .orElseThrow(ResponseError.NotFound.USER_CHALLENGE_NOT_EXISTS::getResponseException);

        //신고안됨
        Byte state=0;

        Long filename_seq = confirmRepository.findSeq();

        final Tika tika = new Tika();
        String mimeTypeString = null;
        try {
            // get extension from file content signiture by tika
            mimeTypeString = tika.detect(image.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e.getCause());
        }

        // validate image extension
        if (Constant.IMAGE_MIME_TYPE_LIST.stream().noneMatch(mimeTypeString::startsWith)) {
            throw ResponseError.BadRequest.INVALID_IMAGE.getResponseException(
                    "file type must in (" + String.join(",", Constant.IMAGE_MIME_TYPE_LIST) + ")"
            );
        }

        // get file extension ex) png jpeg
        final String extension = mimeTypeString.substring(mimeTypeString.lastIndexOf('/') + 1);

        final String fileName = System.currentTimeMillis()
                + "_" + UUID.randomUUID().toString().replaceAll("-", "")
                + "." + extension;

        String imgUrl = null;
        try {
            imgUrl = s3ImageService.fileUpload(image.getInputStream(), image.getSize(), image.getContentType(), "confirm", fileName);
        } catch (IOException e) {
            throw new RuntimeException(e.getCause());
        }

        Confirm confirm=Confirm.builder()
                .user(user)
                .challenge(challenge)
                .date(today)
                .imgUrl(imgUrl)
                .confirmState(state)
                .build();

        try {
            confirmRepository.save(confirm);
        } catch (Exception e) {
            throw ResponseError.BadRequest.ALREADY_CREATED.getResponseException(); // for duplicate exception
        }

        userChallengeService.updateUserChallengeResult(userChallenge, today, UserChallengeResultType.COMPLETE);

    }

    @Transactional
    public List<Confirm> getAllConfirmList(ReqList req){
        return confirmRepository.findAllConfirmList(req.getPrevLastSeq(),req.getPageSize());
    }

    // 챌린지별 인증 조회
    @Transactional
    public List<Confirm> getConfirmList(Long seq,ReqList req) {
        return confirmRepository.findByChallengeByOrderDesc(seq,req.getPrevLastSeq(),req.getPageSize());
    }

    @Transactional
    public List<Confirm> getUserConfirmList(Long userSeq,ReqList req){
        return confirmRepository.findByUserByOrderDesc(userSeq,req.getPrevLastSeq(),req.getPageSize());
    }

    //인증 하나 조회
    @Transactional
    public Confirm getConfirm(Long confirmSeq) {
        return confirmRepository.findBySeq(confirmSeq);
    }

    //confirmSticker 조회
    @Transactional
    public List<ConfirmSticker> getConfirmStickerList(Long seq){
        return confirmStickerRepository.findAllBySeq(seq);
    }

    @Transactional
    public void deleteConfirm(Long userSeq, Long confirmSeq) {
        User user = userRepository.findById(userSeq)
                .orElseThrow(ResponseError.NotFound.USER_NOT_EXISTS::getResponseException);

        Confirm confirm=getConfirm(confirmSeq);

        if(!confirm.getUser().equals(user)) throw ResponseError.BadRequest.INVALID_USER.getResponseException();

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

    @Transactional
    public void updateConfirmState(Confirm confirm){

        Byte state=1;

        confirmRepository.updateDeclarationState(
                confirm,state
        );
    }

    //신고 생성
    @Transactional
    public void createDeclaration(Long confirmSeq,DeclarationType type){

        Confirm confirm=confirmRepository.findBySeq(confirmSeq);


        updateConfirmState(confirm);

        Declaration declaration=Declaration.builder()
                .confirm(confirm)
                .declarationType(type)
                .build();

        try {
            declarationRepository.save(declaration);
        } catch (Exception e) {
            throw ResponseError.BadRequest.ALREADY_CREATED.getResponseException(); // for duplicate exception
        }

    }

    @Transactional
    public List<StickerImg> getStickerImgList(List<Long> stickerSeqList){
        return stickerImgRepository.findAllBySeq(stickerSeqList);
    }

    public List<UserStickerImg> getUserStickerImg(Long userSeq) {

            List<Long> userStickerSeqList=userStickerRepository.stickerSeqfindByUserSeq(userSeq);

            List<StickerImg> stickerImgList = stickerImgRepository.findAll();
            Set<Long> downloadedStickers = new HashSet();

            for ( Long userStickerSeq : userStickerSeqList) {
                downloadedStickers.add(userStickerSeq);
            }

            List<UserStickerImg> userStickerImgs=new ArrayList<UserStickerImg>();
            for ( StickerImg stickerImg : stickerImgList ) {
                if(downloadedStickers.contains(stickerImg.getSeq())) {
                    userStickerImgs.add(new UserStickerImg(stickerImg, true));
                }
                else
                    userStickerImgs.add(new UserStickerImg(stickerImg, false));

            }

            return userStickerImgs;

    }

    @Transactional
    public List<StickerImg> getStickerImg(Long userSeq) {


        List<StickerImg> stickerImgList = stickerImgRepository.findAll();


        return stickerImgList;

    }

    //UserSticker 생성
    @Transactional
    public void createUserSticker(Long userSeq, ReqUserStickerList userStickerList){

        User user = userRepository.findById(userSeq)
                .orElseThrow(ResponseError.NotFound.USER_NOT_EXISTS::getResponseException);

        List<Long> stickerSeqList=userStickerList.getStickerSeqList();

        List<UserSticker> newUserStickerList=new ArrayList<UserSticker>();


        List<StickerImg> stickerImgList=getStickerImgList(stickerSeqList);
        for(StickerImg stickerImg:stickerImgList){
            UserStickerId userStickerId=new UserStickerId(user,stickerImg);
                 UserSticker userSticker=UserSticker.builder()
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
    public void createConfirmSticker(Long userSeq,Long confirmSeq,ReqConfirmSticker reqConfirmSticker){

        User user = userRepository.findById(userSeq)
                .orElseThrow(ResponseError.NotFound.USER_NOT_EXISTS::getResponseException);

        Confirm confirm=confirmRepository.findBySeq(confirmSeq);

        //confirmSticker는 자기 자신에 스토리에 스티커를 붙이지 못한다.
        if(userSeq==confirm.getUser().getSeq()) throw ResponseError.BadRequest.INVALID_USER.getResponseException();

        //true면 존재한다.
        Boolean createdCheck=confirmStickerRepository.findByUserAndConfirmSeq(userSeq,confirmSeq);

        //하나만 붙일수있다. confirmSeq가 같고 userSeq가 같으면 못붙인다.
        if(createdCheck==true)  throw ResponseError.BadRequest.ALREADY_CREATED.getResponseException();

        Optional<StickerImg> stickerImg=stickerImgRepository.findById(reqConfirmSticker.getStickerSeq());

        UserStickerId userStickerId=new UserStickerId(user,stickerImg.get());

        UserSticker userSticker=userStickerRepository.findById(userStickerId)
                .orElseThrow(ResponseError.NotFound.USER_STICKER_NOT_EXISTS::getResponseException);

        ConfirmSticker confirmSticker=ConfirmSticker.builder()
                .confirm(confirm)
                .user(user)
                .stickerImg(stickerImg.get())
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

}
