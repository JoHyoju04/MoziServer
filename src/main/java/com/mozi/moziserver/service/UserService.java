package com.mozi.moziserver.service;

import com.mozi.moziserver.common.JpaUtil;
import com.mozi.moziserver.common.UserState;
import com.mozi.moziserver.httpException.ResponseError;
import com.mozi.moziserver.model.entity.User;
import com.mozi.moziserver.model.entity.UserAuth;
import com.mozi.moziserver.model.entity.UserFcm;
import com.mozi.moziserver.model.mappedenum.UserAuthType;
import com.mozi.moziserver.model.req.ReqUserSignIn;
import com.mozi.moziserver.model.req.ReqUserSignUp;
import com.mozi.moziserver.repository.UserAuthRepository;
import com.mozi.moziserver.repository.UserFcmRepository;
import com.mozi.moziserver.repository.UserRepository;
import com.mozi.moziserver.security.ReqUserSocialSignIn;
import com.mozi.moziserver.security.ResUserSignIn;
import com.mozi.moziserver.security.UserSocialAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static com.mozi.moziserver.common.Constant.EMAIL_DOMAIN_GROUPS;
import static com.mozi.moziserver.common.Constant.EMAIL_REGEX;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;
    private final UserFcmRepository userFcmRepository;
    private final AuthenticationManager authenticationManager;
    private final UserSocialAuthenticationProvider userSocialAuthenticationProvider;
    private final PasswordEncoder passwordEncoder;
    private final EmailAuthService emailAuthService;

    public User signUp(ReqUserSignUp reqUserSignUp) {

        if (reqUserSignUp.getType() != UserAuthType.EMAIL) {
            throw ResponseError.BadRequest.INVALID_EMAIL.getResponseException();
        }

        String email = reqUserSignUp.getId();
        if (!isValidEmail(email)) {
            throw ResponseError.BadRequest.INVALID_EMAIL.getResponseException();
        }

        String emailId = getEmailId(email);
        List<String> currentDomainGroup = getCurrentEmailDomainGroup(email);
        if (emailId == null || currentDomainGroup == null) {
            throw ResponseError.BadRequest.INVALID_EMAIL.getResponseException();
        }

        for (String domain : currentDomainGroup) {
            boolean isExists = userAuthRepository.findUserAuthByTypeAndId(UserAuthType.EMAIL, emailId + "@" + domain).isPresent();
            if (isExists) {
                throw ResponseError.BadRequest.ALREADY_EXISTS_EMAIL.getResponseException();
            }
        }

        UserAuth userAuth = new UserAuth();
        userAuth.setId(reqUserSignUp.getId());
        userAuth.setPw(passwordEncoder.encode(reqUserSignUp.getPw()));
        userAuth.setType(UserAuthType.EMAIL);

        User user = new User();
        userRepository.save(user);

        if (user.getSeq() == null) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }

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
                else if (req.getType() == UserAuthType.APPLE) appleSignUp(req);

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
            User user = userRepository.findById(((ResUserSignIn) auth).getUserSeq())
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

        if (kakaoSocialId == null) {
            return;
        }

        User user = new User();
        userRepository.save(user);

        if (user.getSeq() == null) {
            return;
        }

        UserAuth userAuth = new UserAuth();
        userAuth.setType(UserAuthType.KAKAO);
        userAuth.setId(kakaoSocialId);
        userAuth.setUser(user);

        userAuthRepository.save(userAuth);
    }

    private void appleSignUp(ReqUserSignIn reqUserSignIn) {
        final String identityToken = reqUserSignIn.getId();

        final String appleSocialId = userSocialAuthenticationProvider.getAppleSocialId(identityToken);

        if (appleSocialId == null) {
            return;
        }

        User user = new User();
        userRepository.save(user);

        if (user.getSeq() == null) {
            return;
        }

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

    public Optional<UserAuth> findUserAuthByTypeAndId(UserAuthType type, String id) {
        return userAuthRepository.findUserAuthByTypeAndId(type, id);
    }

    public void updateNickname(User user, String nickname) {

        user.setNickName(nickname);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            if (JpaUtil.isDuplicateKeyException(e)) {
                throw ResponseError.BadRequest.ALREADY_EXISTS_NICKNAME.getResponseException();
            }
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        } catch (Exception e) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }
    }

    public void updatePw(User user, String pw) {

        UserAuth userAuth = userAuthRepository.findByUserAndType(user, UserAuthType.EMAIL);
        if (userAuth == null) {
            throw ResponseError.BadRequest.SOCIAL_LOGIN_USER.getResponseException("social login user cannot change password");
        }

        userAuth.setPw(passwordEncoder.encode(pw));

        try {
            userRepository.save(user);
        } catch (Exception e) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }
    }

    public void updateEmail(User user, String email) {
        UserAuth userAuth = userAuthRepository.findByUser(user);

        emailAuthService.sendResetEmailEmail(userAuth, email);
    }

    public UserAuth findUserAuthByNicknameAndPw(String nickName, String pw) {

        User user = userRepository.findByNickName(nickName);

        if (user == null) {
            throw ResponseError.NotFound.NICKNAME_NOT_EXISTS.getResponseException();
        }

        UserAuth userAuth = userAuthRepository.findByUser(user);

        if (!passwordEncoder.matches(pw, userAuth.getPw())) {
            throw ResponseError.NotFound.USER_NOT_EXISTS.getResponseException("password not matched");
        }

        return userAuth;
    }

    public boolean checkEmailDuplicate(String email) {

        if (!isValidEmail(email)) {
            throw ResponseError.BadRequest.INVALID_EMAIL.getResponseException();
        }

        String emailId = getEmailId(email);
        List<String> currentDomainGroup = getCurrentEmailDomainGroup(email);
        if (emailId == null || currentDomainGroup == null) {
            throw ResponseError.BadRequest.INVALID_EMAIL.getResponseException();
        }

        for (String domain : currentDomainGroup) {
            boolean isExists = userAuthRepository.findUserAuthByTypeAndId(UserAuthType.EMAIL, emailId + "@" + domain).isPresent();
            if (isExists) {
                return true;
            }
        }

        User user = userAuthRepository.findUserSeqByEmail(email);

        return !(user == null || user.getState() == UserState.DELETED);
    }

    public boolean checkNickNameDuplicate(String nickName) {
        return userRepository.existsByNickName(nickName);
    }

    private boolean isValidEmail(String email) {
        if (!email.matches(EMAIL_REGEX)) {
            return false;
        }

        List<String> currentDomainGroup = getCurrentEmailDomainGroup(email);

        return currentDomainGroup != null;
    }

    private List<String> getCurrentEmailDomainGroup(String email) {
        int atIndex = email.lastIndexOf('@');
        String emailDomain = email.substring(atIndex + 1).toLowerCase();

        for (List<String> domainGroup : EMAIL_DOMAIN_GROUPS) {
            if (domainGroup.contains(emailDomain)) {
                return domainGroup;
            }
        }

        return null;
    }

    private String getEmailId(String email) {
        int atIndex = email.lastIndexOf('@');
        if (atIndex < 0) {
            return null;
        }
        return email.substring(0, atIndex);
    }

    public Optional<User> getUserBySeq(Long userSeq) {
        return userRepository.findById(userSeq);
    }

    public void upsertUserFcm(User user, String deviceId, String token) {
        UserFcm userFcm = new UserFcm();
        userFcm.setDeviceId(deviceId);
        userFcm.setToken(token);
        userFcm.setUser(user);

        try {
            userFcmRepository.save(userFcm);
            return;
        } catch (DataIntegrityViolationException e) {
            if (!JpaUtil.isDuplicateKeyException(e)) {
                throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
            }
        }

        userFcm = userFcmRepository.findUserFcmByDeviceId(deviceId)
                .orElseThrow(ResponseError.InternalServerError.UNEXPECTED_ERROR::getResponseException);
        userFcm.setToken(token);
        userFcm.setUser(user);

        userFcmRepository.save(userFcm);
    }
}
