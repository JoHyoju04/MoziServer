package com.mozi.moziserver.service;


import com.mozi.moziserver.common.Constant;
import com.mozi.moziserver.httpException.ResponseError;
import com.mozi.moziserver.model.entity.*;
import com.mozi.moziserver.model.mappedenum.PointReasonType;
import com.mozi.moziserver.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IslandService {
    private final UserRepository userRepository;
    private final IslandRepository islandRepository;
    private final UserIslandRepository userIslandRepository;
    private final IslandImgRepository islandImgRepository;
    private final UserRewardRepository userRewardRepository;
    private final S3ImageService s3ImageService;
    private final PlatformTransactionManager transactionManager;
    private final UserRewardService userRewardService;
    private final AnimalRepository animalRepository;
    private final PostboxMessageAnimalService postboxMessageAnimalService;

    private Island getIsland(Integer type) {
        Island island = islandRepository.findById(type)
                .orElseThrow(ResponseError.NotFound.ISLAND_NOT_EXISTS::getResponseException);

        return island;
    }

    private IslandImg getIslandImg(Integer type, Integer level) {
        IslandImg islandImg = islandImgRepository.findByTypeAndLevel(type, level);

        if(islandImg == null){
            throw ResponseError.NotFound.ISLAND_IMG_NOT_EXISTS.getResponseException();
        }

        return islandImg;
    }

    public List<UserIsland> getUserIslandList(Long userSeq) {
        User user = userRepository.findById(userSeq)
                .orElseThrow(ResponseError.NotFound.USER_NOT_EXISTS::getResponseException);

        return userIslandRepository.findAllByUserOrderByType(user);
    }

    @Transactional
    public void openUserIsland(Long userSeq) {
        User user = userRepository.findById(userSeq)
                .orElseThrow(ResponseError.NotFound.USER_NOT_EXISTS::getResponseException);

        // ?????? ?????? ?????? ?????? ?????? ????????? ?????? ??????????????? ????????????.
        List<UserIsland> userIslandList = userIslandRepository.findAllByUserOrderByType(user);
        UserIsland lastUserIsland =  userIslandList.get(userIslandList.size()-1);
        if (lastUserIsland.getType() == Constant.lastIslandType) {
            throw ResponseError.BadRequest.INVALID_USER_ISLAND_OPEN.getResponseException();
        }

        // ?????? ???????????? 450??? ???????????? ????????????.
        int nextIslandType = lastUserIsland.getType() + 1;
        Island nextIsland = islandRepository.getById(nextIslandType);
        UserReward userReward = userRewardRepository.findByUser(user);
        if (userReward.getPoint() < nextIsland.getMaxPoint()) {
            throw ResponseError.BadRequest.INVALID_USER_ISLAND_OPEN.getResponseException();
        }

        // ?????? ???????????? ?????? ??????????????? ??????????????? ????????????.
        if (lastUserIsland.getRewardLevel() != lastUserIsland.getIsland().getMaxRewardLevel()) {
            throw ResponseError.BadRequest.INVALID_USER_ISLAND_OPEN.getResponseException();
        }

        // ?????? ?????? ????????????.
        // UserIsland ??????
        // UserReward 450??????
        UserIsland nextUserIsland = UserIsland.builder()
                .type(nextIslandType)
                .user(user)
                .rewardLevel(1)
                .build();

        userIslandRepository.save(nextUserIsland);

        //????????? ?????? ??????: 1.????????? ?????? ????????? ?????? ??????
        Animal nextAnimal = animalRepository.findByIslandTypeAndIslandLevel(nextIslandType,2);

        //????????? ?????? ??????: 2.????????? ?????? ??????
        postboxMessageAnimalService.createPostboxMessageAnimal(user,nextAnimal);

        userRewardRepository.decrementPoint(user.getSeq(), lastUserIsland.getIsland().getMaxPoint());

        //UserPointRecord??? ??????
        userRewardService.createUserPointRecord(user, PointReasonType.ISLAND_OPEN,-450);
    }

    public List<Island> getIslandList() {
        return islandRepository.findAllByOrderByTypeAsc();
    }

    public void createIsland(
            String name,
            Integer type,
            String description,
            Integer maxPoint,
            Integer maxRewardLevel,
            List<MultipartFile> islandImgUrlList,
            List<MultipartFile> islandThumbnailImgUrlList
    ){
        final Optional<Island> optionalIsland=islandRepository.findById(type);

        if(optionalIsland.isPresent()){
            throw ResponseError.BadRequest.ALREADY_CREATED.getResponseException("already created island");
        }

        for(int i = 0; i < Constant.islandMaxLevel; i++){
            String imgUrl=islandUploadFile(islandImgUrlList.get(i),i+1);
            String thumbnailImgUrl = islandUploadFile(islandThumbnailImgUrlList.get(i),i+1);

            final IslandImg islandImg=IslandImg.builder()
                    .type(type)
                    .level(i+1)
                    .imgUrl(imgUrl)
                    .thumbnailImgUrl(thumbnailImgUrl)
                    .build();

            withTransaction(()->{
                try {
                    islandImgRepository.save(islandImg);
                } catch (Exception e) {
                    throw ResponseError.BadRequest.ALREADY_CREATED.getResponseException(); // for duplicate exception
                }
            });
        }

        final Island island = Island.builder()
                .type(type)
                .name(name)
                .description(description)
                .maxPoint(maxPoint)
                .maxRewardLevel(maxRewardLevel)
                .build();

        withTransaction(()->{
            try {
                islandRepository.save(island);
            } catch (Exception e) {
                throw ResponseError.BadRequest.ALREADY_CREATED.getResponseException(); // for duplicate exception
            }
        });

    }

    @Transactional
    public void updateIsland(
            String name,
            Integer type,
            String description,
            Integer maxPoint,
            Integer maxRewardLevel
    ) {

        final Island island=getIsland(type);

        if (name != null && name.length() != 0 ) {
            island.setName(name);
        }

        if (description != null && description.length() != 0) {
            island.setDescription(description);
        }

        if(maxPoint != 0 ){
            island.setMaxPoint(maxPoint);
        }

        if(maxRewardLevel !=0 ){
            island.setMaxRewardLevel(maxRewardLevel);
        }

        try {
            islandRepository.save(island);
        } catch (Exception e) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }
    }

    public void updateIslandImg(
            Integer type,
            Integer level,
            MultipartFile islandImgFile,
            MultipartFile islandThumbnailImgFile
    ) {

        final IslandImg islandImg = getIslandImg(type, level);

        String islandImgUrl = null;
        if (islandImgFile != null) {
            try {
                islandImgUrl = islandUploadFile(islandImgFile,level);
            } catch (Exception e) {
                throw new RuntimeException(e.getCause());
            }
            islandImg.setImgUrl(islandImgUrl);
        }

        String islandThumbnailImgUrl = null;
        if (islandThumbnailImgFile != null) {
            try {
                islandThumbnailImgUrl = islandUploadFile(islandThumbnailImgFile,level);
            } catch (Exception e) {
                throw new RuntimeException(e.getCause());
            }
            islandImg.setImgUrl(islandThumbnailImgUrl);
        }

        if (islandImgUrl != null) {
            islandImg.setImgUrl(islandImgUrl);
        }
        if (islandThumbnailImgUrl != null) {
            islandImg.setThumbnailImgUrl(islandThumbnailImgUrl);
        }

        withTransaction(()->{
            try {
                islandImgRepository.save(islandImg);
            } catch (Exception e) {
                throw ResponseError.BadRequest.ALREADY_CREATED.getResponseException(); // for duplicate exception
            }
        });
    }

    public String islandUploadFile(MultipartFile multipartFile,Integer level){
        try {
            return s3ImageService.uploadFile(multipartFile, "islandImgUrlLevel"+level.toString());
        } catch (Exception e) {
            throw new RuntimeException(e.getCause());
        }
    }

    @Transactional
    public void firstCreateUserIsland(User user){

        UserIsland firstUserIsland = UserIsland.builder()
                .type(1)
                .user(user)
                .rewardLevel(1)
                .build();
        userIslandRepository.save(firstUserIsland);

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
