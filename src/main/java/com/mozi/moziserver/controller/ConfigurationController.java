package com.mozi.moziserver.controller;
import com.mozi.moziserver.model.entity.User;
import com.mozi.moziserver.model.entity.UserAuth;
import com.mozi.moziserver.model.req.*;
import com.mozi.moziserver.model.res.ResBoardList;
import com.mozi.moziserver.model.res.ResDuplicationCheck;
import com.mozi.moziserver.model.res.ResMyPage;
import com.mozi.moziserver.security.SessionUser;
import com.mozi.moziserver.service.*;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ConfigurationController {

    private final QuestionService questionService;
    private final BoardService boardService;
    private final SuggestionService suggestionService;
    private final MyPageService myPageService;

    @ApiOperation("문의 등록")
    @PostMapping("/v1/questions")
    public ResponseEntity<Void> createQuestion(
            @ApiParam(hidden = true) @SessionUser Long userSeq,
            @RequestBody @Valid ReqQuestionCreate reqQuestionCreate
    ) {
        questionService.createQuestion(userSeq, reqQuestionCreate);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @ApiOperation("공지사항 보기")
    @GetMapping("/v1/users/v2/boards")
    public List<ResBoardList> getBoardListByCreatedAt(
            @ApiParam(hidden = true) @SessionUser Long userSeq,
            @Valid ReqBasic req
    ) {
       return boardService.getAllBoardListByCreatedAt(userSeq, req)
               .stream()
               .map(ResBoardList::of)
               .collect(Collectors.toList());
    }

    @ApiOperation("챌린지 제안하기")
    @PostMapping("/v1/users/v2/suggestions")
    public ResponseEntity<Void> createSuggestion(
            @ApiParam(hidden = true) @SessionUser Long userSeq,
            @RequestBody @Valid ReqSuggestionCreate reqSuggestionCreate
            ) {
        suggestionService.createSuggestion(userSeq, reqSuggestionCreate);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // get - 닉네임, 이메일
    @ApiOperation("프로필 편집_GET")
    @GetMapping("/v1/users/mypages")
    public ResMyPage getUserInfo(
            @ApiParam(hidden = true) @SessionUser Long userSeq
    ) {
        UserAuth userAuth = myPageService.getUserInfo(userSeq);
        return ResMyPage.of(userAuth);
    }

    // 닉네임 수정
    @ApiOperation("프로필 수정_POST_닉네임")
    @PostMapping("/v1/users/mypage/nicknames")
    public ResponseEntity<Void> updateUserNickName(
            @ApiParam(hidden = true) @SessionUser Long userSeq,
            @RequestBody @Valid ReqUserNickNameAndPw req
    ) {
        myPageService.updateUserNickName(userSeq, req.getNickName());
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // 비밀번호 수정
    @ApiOperation("프로필 수정_POST_비밀번호")
    @PostMapping("/v1/users/mypage/passwords")
    public ResponseEntity<Void> updateUserPassword(
            @ApiParam(hidden = true) @SessionUser Long userSeq,
            @RequestBody @Valid ReqUserNickNameAndPw req
    ) {
        myPageService.updateUserPassword(userSeq, req.getPw());
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // 닉네임 중복확인
    @ApiOperation("닉네임 중복확인")
    @PostMapping("/v1/users/mypages/nickname-duplications")
    public Boolean getUserNickName(
            @ApiParam(hidden = true) @SessionUser Long userSeq,
            @RequestBody @Valid ReqNickName reqNickName
    ) {
        UserAuth userAuth = myPageService.getAllNickName(userSeq, reqNickName.getNickName());
        try {
            if(reqNickName.getNickName().equals(ResDuplicationCheck.of(userAuth).getNickName())) {
                return Boolean.TRUE;
            }
        }
        catch (NullPointerException e) {
            return Boolean.FALSE;
        }
        return Boolean.FALSE;
    }

    @ApiOperation("이메일 중복확인")
    @PostMapping("/v1/users/mypages/email-duplications")
    public Boolean getUserEmail(
            @ApiParam(hidden = true) @SessionUser Long userSeq,
            @RequestBody @Valid ReqEmail reqEmail
    ) {
        UserAuth userAuth = myPageService.getAllEmail(userSeq, reqEmail.getEmail());

        String email = (userAuth != null ? ResDuplicationCheck.of(userAuth).getEmail() : null);
        String id = (userAuth != null ? ResDuplicationCheck.of(userAuth).getId() : null);

        if(reqEmail.getEmail().equals(email) || reqEmail.getEmail().equals(id)) {
            return Boolean.TRUE;
        }
        else return Boolean.FALSE;

//        try {
//            if(reqEmail.getEmail().equals(email) || reqEmail.getEmail().equals(id)) {
//                return Boolean.TRUE;
//            }
//        }
//        catch (Exception e) {
//            return Boolean.FALSE;
//        }
//        return Boolean.FALSE;
    }

    @ApiOperation("이메일 인증 후 수정")
    @PostMapping(value = "/v1/users/mypages/email-auths", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> authUserEmail(
            @ApiParam(hidden = true) @SessionUser Long userSeq,
            @RequestBody @Valid ReqAuthEmail reqAuthEmail
            ) {
        myPageService.authUserEmail(userSeq, reqAuthEmail.getEmail());
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
