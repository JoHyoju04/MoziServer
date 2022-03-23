package com.mozi.moziserver.controller;

import com.mozi.moziserver.httpException.ResponseError;
import com.mozi.moziserver.model.entity.*;
import com.mozi.moziserver.model.mappedenum.DeclarationType;
import com.mozi.moziserver.model.req.*;
import com.mozi.moziserver.model.res.*;
import com.mozi.moziserver.security.SessionUser;
import com.mozi.moziserver.service.ConfirmService;
import com.mozi.moziserver.model.entity.UserStickerImg;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ConfirmController {

    private final ConfirmService confirmService;

    @ApiOperation("스토리 생성")
    @PostMapping("/v1/challenges/{challenge_seq}/confirms")
    public ResponseEntity<Void> createConfirm(
            @ApiParam(hidden = true) @SessionUser Long userSeq,
            @PathVariable("challenge_seq") Long challengeSeq,
            @RequestPart MultipartFile image
    ){
        if (image == null) {
            throw ResponseError.BadRequest.INVALID_IMAGE.getResponseException("need to images");
        }

        confirmService.createConfirm(userSeq, challengeSeq, image);
        return new ResponseEntity<>(HttpStatus.OK);
    }


    @ApiOperation("스토리 전체 조회")
    @GetMapping("/v1/challenges/confirms")
    public List<ResConfirmList> getAllConfirmList(
            @Valid ReqList req
    ) {

        return confirmService.getAllConfirmList(req)
                .stream()
                .map(ResConfirmList::of)
                .collect(Collectors.toList());
    }

    @ApiOperation("챌린지별 스토리 전체 조회")
    @GetMapping("/v1/challenges/{challenge_seq}/confirms")
    public List<ResConfirmList> getConfirmList(
            @PathVariable("challenge_seq") Long challengeSeq,
            @Valid ReqList req
    ) {

        return confirmService.getConfirmList(challengeSeq,req)
                .stream()
                .map(ResConfirmList::of)
                .collect(Collectors.toList());
    }

    //최신순
    @ApiOperation("본인 스토리 전체 조회")
    @GetMapping("/v1/users/confirms")
    public List<ResUserConfirmList> getUserConfirmList(
            @ApiParam(hidden = true) @SessionUser Long userSeq,
            @Valid ReqList req
    ) {

        return confirmService.getUserConfirmList(userSeq,req)
                .stream()
                .map(ResUserConfirmList::of)
                .collect(Collectors.toList());
    }

    @ApiOperation("스토리 하나 조회")
    @GetMapping("/v1/confirms/{confirm_seq}")
    public ResConfirm getConfirm(
            @PathVariable("confirm_seq") Long confirmSeq
    ) {
        Confirm confirm=confirmService.getConfirm(confirmSeq);

        List<ConfirmSticker> confirmStickerList=confirmService.getConfirmStickerList(confirmSeq);

        return ResConfirm.of(confirm,confirmStickerList);
    }

    @ApiOperation("스토리 삭제")
    @DeleteMapping("/v1/confirms/{confirm_seq}")
    public ResponseEntity<Void> deleteConfirm(
            @ApiParam(hidden = true) @SessionUser Long mySeq,
            @PathVariable("confirm_seq") Long confirmSeq
    ){
        confirmService.deleteConfirm(mySeq,confirmSeq);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @ApiOperation("신고 생성")
    @PostMapping("/v1/confirms/{confirm_seq}/declarations")
    public ResponseEntity<Void> createDeclaration(
            @PathVariable("confirm_seq") Long confirmSeq,
            @RequestBody @Valid ReqDeclarationCreate req
    ){
        confirmService.createDeclaration(confirmSeq,req.getType());
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @ApiOperation("스티커 전체 조회")
    @GetMapping("/v1/stickerImgs")
    public List<ResStickerImg> getStickerList(
            @ApiParam(hidden = true) @SessionUser Long userSeq
    ) {
        List<StickerImg> stickerImgList=confirmService.getStickerImg(userSeq);

        return stickerImgList
                .stream()
                .map(ResStickerImg::of)
                .collect(Collectors.toList());
    }


    @ApiOperation("스티커 생성(부착)")
    @PostMapping("/v1/confirms/{confirm_seq}/confirm-stickers")
    public ResponseEntity<Void> createConfirmSticker(
            @ApiParam(hidden = true) @SessionUser Long mySeq,
            @RequestBody @Valid ReqConfirmSticker reqConfirmSticker,
            @PathVariable("confirm_seq") Long confirmSeq
    ){
        confirmService.createConfirmSticker(mySeq,confirmSeq, reqConfirmSticker);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}