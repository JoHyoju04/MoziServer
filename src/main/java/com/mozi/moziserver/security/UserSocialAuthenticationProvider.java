package com.mozi.moziserver.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mozi.moziserver.model.entity.User;
import com.mozi.moziserver.model.entity.UserAuth;
import com.mozi.moziserver.model.mappedenum.UserAuthType;
import com.mozi.moziserver.repository.UserAuthRepository;
import com.mozi.moziserver.rest.AppleClient;
import com.mozi.moziserver.rest.KakaoClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

// TODO
@Component
@RequiredArgsConstructor
public class UserSocialAuthenticationProvider implements AuthenticationProvider {

    private final UserAuthRepository userAuthRepository;

    private final KakaoClient kakaoClient;

    private final AppleClient appleClient;

    @Value("${social.kakao.appId}")
    private Long kakaoAppId;

    @Value("${social.apple.appId}")
    private String appleAppId;

    private static ObjectMapper objectMapper = new ObjectMapper();


//    @Autowired
//    UserAuthPwAspect userAuthPwAspect;

//    @Autowired
//    FacebookRestClient facebookClient;
//    @Value("${social.facebook.appid}")
//    String facebookAppId;
//    @Value("${social.facebook.secret}")
//    String facebookSecret;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!(authentication instanceof ReqUserSocialSignIn))
            return null;

        ReqUserSocialSignIn reqUserSocialSignIn = (ReqUserSocialSignIn) authentication;

        if (reqUserSocialSignIn.getType() == null || reqUserSocialSignIn.getId() == null)
            return null;

        UserAuth userAuth = new UserAuth();
        userAuth.setType(reqUserSocialSignIn.getType());
        userAuth.setId(reqUserSocialSignIn.getId());

        String socialId = null;

        if (userAuth.getType().isSocial()) {
            socialId =
                    userAuth.getType() == UserAuthType.KAKAO ? getKakaoSocialId(userAuth.getId()) :
                            userAuth.getType() == UserAuthType.APPLE ? getAppleSocialId(userAuth.getId()) : null;

//                type == UserAuthType.FACEBOOK ? getFacebookSocialId(userAuth) :
//                type == UserAuthType.NAVER ? getNaverSocialId(userAuth) :
//                type == UserAuthType.GOOGLE ? getGoogleSocialId(userAuth)

            if (socialId == null)
                return new ResUserSignInFail(); // new ResUserSocialSignIn();
        }

        User user = getUserSeqByTypeAndSocialId(userAuth.getType(), socialId);
        userAuth.setUser(user);

        return user == null ? new ResUserSignInFail() : new ResUserSocialSignIn(userAuth);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(ReqUserSocialSignIn.class);
    }

    private User getUserSeqByTypeAndSocialId(UserAuthType type, String socialId) {

        Optional<UserAuth> userAuthOptional = userAuthRepository.findUserAuthByTypeAndId(type, socialId);

        if (userAuthOptional.isEmpty())
            return null;

        return userAuthOptional.get().getUser();
    }

    public String getKakaoSocialId(String accessToken) {

        ResponseEntity<KakaoClient.ResUserByAccessToken> response = null;
        try {
            response = kakaoClient.getUserMeByAccessToken("Bearer " + accessToken);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (response == null
                || !response.getStatusCode().is2xxSuccessful()
                || response.getBody() == null
                || response.getBody().getAppId() == null
                || !response.getBody().getAppId().equals(kakaoAppId)) {
            return null;
        }

        return response.getBody().getId().toString(); // kakao user id
    }

    public String getAppleSocialId(String identityToken) {

        Jwt jwt = getJwt(identityToken);

        if (jwt.getAudience().stream().noneMatch(appleAppId::equals)) {
            throw new RuntimeException("invalid aud (" + jwt.getAudience() + ")");
        }

        return jwt.getSubject();
    }

    private Jwt getJwt(String identityToken) {
        final Map<String, String> headerMap = Optional.ofNullable(identityToken)
                .map(token -> token.substring(0, token.indexOf('.')))
                .map(header -> new String(Base64.getDecoder().decode(header), StandardCharsets.UTF_8))
                .map(header -> { try { return objectMapper.readValue(header, new TypeReference<Map<String,String>>(){}); } catch (Exception e) { return null; } } )
                .orElseThrow(() -> new RuntimeException("fail get header from indentityToken"));

        AppleClient.KeyListRes keyListRes = appleClient.getAuthPublicKeys();

        AppleClient.KeyRes keyRes = keyListRes.getKeys().stream()
                .filter(key -> key.getKid().equals(headerMap.get("kid")) && key.getAlg().equals(headerMap.get("alg")))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("fail find key"));

        RSAPublicKey publicKey = createRsaPublicKey(keyRes);

        return NimbusJwtDecoder.withPublicKey(publicKey)
                .build()
                .decode(identityToken);
    }

    private RSAPublicKey createRsaPublicKey(AppleClient.KeyRes keyRes) {
        BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(keyRes.getN()));
        BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(keyRes.getE()));

        RSAPublicKeySpec rsaPublicKeySpec = new RSAPublicKeySpec(n, e);

        KeyFactory keyFactory;
        try {
            keyFactory = KeyFactory.getInstance(keyRes.getKty());
        } catch (NoSuchAlgorithmException ex) {
            ex.printStackTrace();
            throw new RuntimeException("fail get instance KeyFactory " + keyRes.getKty());
        }

        try {
            return (RSAPublicKey) keyFactory.generatePublic(rsaPublicKeySpec);
        } catch (InvalidKeySpecException ex) {
            ex.printStackTrace();
            throw new RuntimeException("fail generate RSAPublicKey");
        }
    }

//    private String getFacebookSocialId(UserAuth userAuth) {
//        final String accessToken = userAuth.getId();
//
//        final FacebookRestClient.FacebookUserInfo facebookUserInfo = facebookClient.getUserInfo(
//                accessToken,
//                facebookAppId,
//                facebookSecret
//        );
//
//        if(facebookUserInfo == null || StringUtils.isEmpty(facebookUserInfo.getId()))
//            return null;
//
//        return facebookUserInfo.getId();
//    }

//    private String getNaverSocialId(UserAuth userAuth) {
//        return null; // TODO
//    }

//    private String getGoogleSocialId(UserAuth userAuth) {
//        return null; // TODO
//    }
}
