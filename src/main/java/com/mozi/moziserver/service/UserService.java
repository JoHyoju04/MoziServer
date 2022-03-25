package com.mozi.moziserver.service;

import com.mozi.moziserver.common.UserState;
import com.mozi.moziserver.httpException.ResponseError;
import com.mozi.moziserver.model.entity.User;
import com.mozi.moziserver.model.entity.UserAuth;
import com.mozi.moziserver.model.mappedenum.UserAuthType;
import com.mozi.moziserver.model.req.ReqUserNickNameAndPw;
import com.mozi.moziserver.model.req.ReqUserSignIn;
import com.mozi.moziserver.model.req.ReqUserSignUp;
import com.mozi.moziserver.repository.UserAuthRepository;
import com.mozi.moziserver.repository.UserRepository;
import com.mozi.moziserver.security.ReqUserSocialSignIn;
import com.mozi.moziserver.security.ResUserSignIn;
import com.mozi.moziserver.security.UserSocialAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.mozi.moziserver.common.Constant.EMAIL_DOMAIN_GROUPS;
import static com.mozi.moziserver.common.Constant.EMAIL_REGEX;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;
    private final AuthenticationManager authenticationManager;
    private final UserSocialAuthenticationProvider userSocialAuthenticationProvider;
    private final PasswordEncoder passwordEncoder;
    private final EmailAuthService emailAuthService;
//    private final KakaoClient kakaoClient;

    @Value("${social.kakao.appid}")
    Long kakaoAppId;
    @Value("${social.kakao.adminkey}")
    String kakaoAdminKey;

//    @Value("${social.facebook.appid}")
//    String facebookAppId;
//    @Value("${social.facebook.secret}")
//    String facebookSecret;

    public User signUp(ReqUserSignUp reqUserSignUp) {

        if (reqUserSignUp.getType() != UserAuthType.EMAIL) {
            throw ResponseError.BadRequest.INVALID_EMAIL.getResponseException();
        }

        String email = reqUserSignUp.getId();

        if (!email.matches(EMAIL_REGEX)){
            throw ResponseError.BadRequest.INVALID_EMAIL.getResponseException();
        }

        int atIndex = email.lastIndexOf('@');
        String emailIdWithAt = email.substring(0, atIndex+1);
        String emailDomain = email.substring(atIndex+1).toLowerCase();
        List<String> currentDomainGroup = null;

        for(List<String> domainGroup : EMAIL_DOMAIN_GROUPS) {
            if(domainGroup.contains(emailDomain)) {
                currentDomainGroup = domainGroup;
                break;
            }
        }

        if(currentDomainGroup == null) {
            throw ResponseError.BadRequest.INVALID_EMAIL.getResponseException();
        }


        for(String domain : currentDomainGroup) {
            boolean isExists = userAuthRepository.findUserAuthByTypeAndId(UserAuthType.EMAIL, emailIdWithAt + domain).isPresent();
            if(isExists) {
                throw ResponseError.BadRequest.ALREADY_EXISTS_EMAIL.getResponseException();
            }
        }

        UserAuth userAuth = new UserAuth();
        userAuth.setId(reqUserSignUp.getId());
        userAuth.setPw(passwordEncoder.encode(reqUserSignUp.getPw()));

        User user = new User();
        userRepository.save(user);

        if (user.getSeq() == null)
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();

        userAuth.setUser(user);

        emailAuthService.sendJoinEmail(userAuth);

        return user;
    }

    public Authentication signIn(ReqUserSignIn req) {

        Authentication auth = null;

        if (req.getType() == UserAuthType.EMAIL) {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getId(), req.getPw())
            );
        } else {
            ReqUserSocialSignIn reqUserSocialSignIn = new ReqUserSocialSignIn(req);

            auth = authenticationManager.authenticate(reqUserSocialSignIn);

            // TODO checkQuitUser 바꿔야한다.

            if ((auth == null || !auth.isAuthenticated()) && req.getType().isSocial()) {
                if (req.getType() == UserAuthType.KAKAO) kakaoSignUp(req);
                else if(req.getType() == UserAuthType.APPLE) appleSignUp(req);

//            else if(reqUserSignIn.getType() == UserAuthType.FACEBOOK) facebookSignUp(reqUserSignIn);
//            else if(reqUserSignIn.getType() == UserAuthType.NAVER) naverSignUp(reqUserSignIn);
//            else if(reqUserSignIn.getType() == UserAuthType.GOOGLE) googleSignUp(reqUserSignIn);

                auth = authenticationManager.authenticate(reqUserSocialSignIn);
            }
        }

        if (auth == null || !auth.isAuthenticated()) {
            throw ResponseError.BadRequest.BAD_REQUEST.getResponseException();
        }

        if (auth instanceof ResUserSignIn) {
            User user = userRepository.findById(((ResUserSignIn)auth).getUserSeq())
                    .orElseThrow(ResponseError.NotFound.USER_NOT_EXISTS::getResponseException);

            if (user.getState() == UserState.DELETED) {
                throw ResponseError.BadRequest.USER_IS_DELETED.getResponseException();
            }
        }

        return auth;
    }

    private void kakaoSignUp(ReqUserSignIn reqUserSignIn) {
        final String accessToken = reqUserSignIn.getId();

        final String kakaoSocialId = userSocialAuthenticationProvider.getKakaoSocialId(accessToken);

        if (kakaoSocialId == null)
            return;

        User user = new User();
        userRepository.save(user);

        if (user.getSeq() == null)
            return;

        UserAuth userAuth = new UserAuth();
        userAuth.setType(UserAuthType.KAKAO);
        userAuth.setId(kakaoSocialId);
        userAuth.setUser(user);

        userAuthRepository.save(userAuth);
    }

    private void appleSignUp(ReqUserSignIn reqUserSignIn) {
        final String identityToken = reqUserSignIn.getId();

        final String appleSocialId = userSocialAuthenticationProvider.getAppleSocialId(identityToken);

        if (appleSocialId == null)
            return;

        User user = new User();
        userRepository.save(user);

        if (user.getSeq() == null)
            return;

        UserAuth userAuth = new UserAuth();
        userAuth.setType(UserAuthType.APPLE);
        userAuth.setId(appleSocialId);
        userAuth.setUser(user);

        userAuthRepository.save(userAuth);
    }

    private void facebookSignUp(ReqUserSignIn reqUserSignIn) {
//        final String accessToken = reqUserSignIn.getId();
//
//        final FacebookRestClient.FacebookUserInfo facebookUserInfo =
//                facebookClient.getUserInfo(accessToken, facebookAppId, facebookSecret);
//
//        if(facebookUserInfo == null || StringUtils.isEmpty(facebookUserInfo.getId()))
//            return;
//
//        String name = facebookUserInfo.getName();
//
//        User user = new User();
//        int insertCount = userRepository.insertUser(user);
//
//        if(user.getSeq() == null)
//            return;
//
//        UserAuth userAuth = new UserAuth();
//        userAuth.setType(UserAuthType.FACEBOOK);
//        userAuth.setId(facebookUserInfo.getId());
//        userAuth.setUserSeq(user.getSeq());
//
//        userAuthRepository.insertUserAuth(userAuth);
    }

    private void naverSignUp(ReqUserSignIn reqUserSignIn) {
        // TODO
    }

    private void googleSignUp(ReqUserSignIn reqUserSignIn) {
        // TODO
    }

    public UserAuth findUserEmail(String nickName,String pw) {

        User user=userRepository.findByNickName(nickName);

        if(user==null)  throw ResponseError.NotFound.USER_NOT_EXISTS.getResponseException();

        UserAuth userAuth=userAuthRepository.findByUserSeqAndPw(user);

        if(!passwordEncoder.matches(pw,userAuth.getPw()))   throw ResponseError.NotFound.USER_PW_NOT_EXITS.getResponseException();

        return userAuth;
    }

    // 이메일 인증
    @Transactional
    public ResponseEntity<String> sendEmail(String email) {

        User user=userAuthRepository.findUserSeqByEmail(email);

         if (!email.matches(EMAIL_REGEX)){
            throw ResponseError.BadRequest.INVALID_EMAIL.getResponseException();
        }

        int atIndex = email.lastIndexOf('@');
        String emailIdWithAt = email.substring(0, atIndex+1);
        String emailDomain = email.substring(atIndex+1).toLowerCase();
        List<String> currentDomainGroup = null;

        for(List<String> domainGroup : EMAIL_DOMAIN_GROUPS) {
            if(domainGroup.contains(emailDomain)) {
                currentDomainGroup = domainGroup;
                break;
            }
        }

        if(currentDomainGroup == null) {
            throw ResponseError.BadRequest.INVALID_EMAIL.getResponseException();
        }

        String token=emailAuthService.createEmailCheckAuth(user,email);

        return new ResponseEntity<>(token, HttpStatus.OK);
    }

    public ResponseEntity<Void> findUserAuth(String email){

        User user=userAuthRepository.findUserSeqByEmail(email);

        if(user==null || user.getState()==UserState.DELETED)  throw ResponseError.NotFound.EMAIL_NOT_EXITS.getResponseException();

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Transactional
    public User findUserByNickName(String nickName){
        User user=userRepository.findByNickName(nickName);

        return user;
    }

}
