package com.mozi.moziserver.service;

import com.mozi.moziserver.common.JpaUtil;
import com.mozi.moziserver.httpException.ResponseError;
import com.mozi.moziserver.model.EmailAuthResult;
import com.mozi.moziserver.model.entity.*;
import com.mozi.moziserver.model.mappedenum.EmailAuthType;
import com.mozi.moziserver.model.mappedenum.UserAuthType;
import com.mozi.moziserver.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.mail.internet.MimeMessage;
import javax.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.mozi.moziserver.common.Constant.PW_REGEX;

@Service
@RequiredArgsConstructor
public class EmailAuthService {

    private final PlatformTransactionManager transactionManager;
    private final JavaMailSender emailSender;
    private final EmailAuthRepository emailAuthRepository;
    private final UserAuthRepository userAuthRepository;
    private final UserRepository userRepository;
    private final UserRewardService userRewardService;

    private final PostboxMessageAnimalService postboxMessageAnimalService;

    private final AnimalRepository animalRepository;

    private final UserIslandRepository userIslandRepository;

    private final IslandService islandService;
    private final PasswordEncoder passwordEncoder;

    @Value("${spring.mail.username}")
    private String emailAddress;

    @Value("${server.domain}")
    private String serverDomain;

    /**
     * ?????? ????????? ??????
     * @param userAuth
     */
    public void sendJoinEmail(UserAuth userAuth) {
        if (userAuth.getType() != UserAuthType.EMAIL) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException("UserAuth seq = " + userAuth.getSeq());
        }

        EmailAuth emailAuth = new EmailAuth();
        emailAuth.setType(EmailAuthType.JOIN);
        emailAuth.setId(userAuth.getId());
        emailAuth.setPw(userAuth.getPw());
        emailAuth.setUser(userAuth.getUser());

        saveEmailAuth(emailAuth);

        boolean isSend = sendEmail(
                emailAuth.getId(),
                "?????? ?????? ??????",
                "<html> <body><h1></h1>" + "<br/>?????? [??????] ????????? ???????????????." + "<form action=\"" + serverDomain + "/email/auth/" + emailAuth.getToken() + "\"> <input type=\"submit\" value=\"??????\" /> </form>"
                        + "</body></html>"
//                "???????????? : " + serverDomain + "/email/auth/" + emailAuth.getToken()
        );

        if (!isSend) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }
    }

    /**
     * ???????????? ?????? ????????? ??????
     * @param userAuth
     */
    public void sendResetPwEmail(UserAuth userAuth) {
        if (userAuth.getType() != UserAuthType.EMAIL) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }

        String tempPassword=getTempPassword();

        userAuth.setPw(passwordEncoder.encode(tempPassword));

        try {
            userAuthRepository.save(userAuth);
        } catch (Exception e) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }

        EmailAuth emailAuth = new EmailAuth();
        emailAuth.setType(EmailAuthType.RESET_PW);
        emailAuth.setId(userAuth.getId());
        emailAuth.setPw(userAuth.getPw());
        emailAuth.setUser(userAuth.getUser());

        saveEmailAuth(emailAuth);

        String content="<h1>???????????? ?????? ??????????????? " + tempPassword + " ?????????." + "????????? ?????? ??????????????? ????????? ????????????</h1>";

        boolean isSend = sendEmail(
                emailAuth.getId(),
                "???????????? ????????? ??????",content
        );

        if (!isSend) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }
    }

    public String getTempPassword(){
        UUID uuidPassword=UUID.randomUUID();

        String tempPassword=uuidPassword.toString().substring(0,8)+"!";

        if (!tempPassword.matches(PW_REGEX)) {
            throw ResponseError.BadRequest.INVALID_PASSWORD.getResponseException();
        }

        return tempPassword;
    }

    /**
     * ????????? ?????? ????????? ??????
     * @param userAuth
     * @param newEmail
     */
    public void sendResetEmailEmail(UserAuth userAuth, String newEmail) {
        if (userAuth.getType() != UserAuthType.EMAIL) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }

        EmailAuth emailAuth = new EmailAuth();
        emailAuth.setType(EmailAuthType.RESET_EMAIL);
        emailAuth.setId(newEmail);
        emailAuth.setPw(userAuth.getPw());
        emailAuth.setUser(userAuth.getUser());

        saveEmailAuth(emailAuth);

        boolean isSend = sendEmail(
                newEmail,
                "????????? ?????? ?????? ??????",
                "<html> <body><h1></h1>" + "<br/>?????? [??????] ????????? ???????????????." + "<form action=\"" + serverDomain + "/email/auth/" + emailAuth.getToken() + "\"> <input type=\"submit\" value=\"??????\" /> </form>"
                        + "</body></html>"
        );

        if (!isSend) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }
    }

    /**
     * ????????? ??????
     * @param token
     * @return
     */
    public EmailAuthResult authEmail(String token) {
        Optional<EmailAuth> emailAuthOptional = emailAuthRepository.findByToken(token);

        if (emailAuthOptional.isEmpty()) {
            return EmailAuthResult.builder()
                    .status(EmailAuthResult.Status.INVALID)
                    .build();
        }

        if (emailAuthOptional.get().isCheckedState()) {
            throw ResponseError.BadRequest.ALREADY_CHECKED_EMAIL.getResponseException();
        }

        EmailAuth emailAuth = emailAuthOptional.get();

        if (emailAuth.getUsedDt() != null) {
            return EmailAuthResult.builder()
                    .type(emailAuth.getType())
                    .status(EmailAuthResult.Status.ALREADY_SUCC)
                    .build();
        }

        Duration duration = Duration.between(emailAuth.getCreatedAt(), LocalDateTime.now());
        boolean isExpired = duration.getSeconds() > emailAuth.getType().getExpiredSeconds();

        if (isExpired) {
            return EmailAuthResult.builder()
                    .type(emailAuth.getType())
                    .status(EmailAuthResult.Status.INVALID)
                    .build();
        }

        switch (emailAuth.getType()) {
            case JOIN:
                authJoin(emailAuth);
                break;
            case RESET_PW: // ?????????X, ??? ????????? -> ???????????? ?????????
                authResetPw(emailAuth);
                break;
            case RESET_EMAIL: // ?????????O, ????????? ????????? ????????? ???
                authResetEmail(emailAuth);
                break;
        }

        emailAuth.setUsedDt(LocalDateTime.now());
        emailAuthRepository.save(emailAuth);

        return EmailAuthResult.builder()
                .type(emailAuth.getType())
                .status(EmailAuthResult.Status.SUCC)
                .build();
    }

    private void saveEmailAuth(EmailAuth emailAuth) {
        for (int retry = 0; retry < 3; retry++) {
            try {
                emailAuth.setToken(
                        UUID.randomUUID().toString().replaceAll("-", "")
                                + UUID.randomUUID().toString().replaceAll("-", "")
                );
                emailAuthRepository.save(emailAuth);
                if (emailAuth.getSeq() != null) {
                    break;
                }
            } catch (DataIntegrityViolationException e) {
                if (JpaUtil.isDuplicateKeyException(e)) {
                    continue;
                }
                throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
            } catch (Exception e) {
                throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
            }
        }

        if (emailAuth.getSeq() == null) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }
    }

    /**
     * ????????? ??????
     * @param to ?????? ?????????
     * @param title ??????
     * @param content ??????
     * @return ?????? ?????? ?????? ?????? ??????
     */

    private boolean sendEmail(String to, String title, String content) {
        MimeMessage message = emailSender.createMimeMessage();
        try {
            MimeMessageHelper messageHelper = new MimeMessageHelper(message, true, "UTF-8");
            messageHelper.setSubject(title);
            messageHelper.setTo(to);
            messageHelper.setFrom(emailAddress);
            messageHelper.setText(content, true);
            emailSender.send(message);
        } catch (Exception e) {
            throw ResponseError.BadRequest.BAD_REQUEST.getResponseException();
        }

        return true;
    }
//    private boolean sendEmail(String to, String title, String content) {
//        SimpleMailMessage message = new SimpleMailMessage();
//        message.setFrom(emailAddress);
//        message.setTo(to);
//        message.setSubject(title);
//        message.setText(content);
//        emailSender.send(message); // TODO handling throws MailException
//
//        return true;
//    }

    private void authJoin(EmailAuth emailAuth) {
        UserAuth userAuth = new UserAuth();
        userAuth.setType(UserAuthType.EMAIL);
        userAuth.setId(emailAuth.getId());
        userAuth.setPw(emailAuth.getPw());
        userAuth.setUser(emailAuth.getUser());

        try {
            withTransaction(() -> {
                userAuthRepository.save(userAuth);

                User user = userAuth.getUser();
                user.setEmail(userAuth.getId());
                userRepository.save(user);

                // UserIsland ??????
                islandService.firstCreateUserIsland(user);

                //????????? ?????? ??????
                Animal firstAnimal = animalRepository.findByIslandTypeAndIslandLevel(1,2);
                postboxMessageAnimalService.createPostboxMessageAnimal(user,firstAnimal);

                //UserReword ??????
                userRewardService.firstCreateUserReward(user);
            });
        } catch (DataIntegrityViolationException e) {
            if (JpaUtil.isDuplicateKeyException(e)) {
                throw ResponseError.BadRequest.ALREADY_EXISTS_EMAIL.getResponseException();
            }
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        } catch (Exception e) {
            throw ResponseError.BadRequest.ALREADY_EXISTS_EMAIL.getResponseException();
        }

        if (userAuth.getSeq() == null) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }
    }

    private void authResetPw(EmailAuth emailAuth) {

        UserAuth userAuth = userAuthRepository.findByUserAndType(emailAuth.getUser(), UserAuthType.EMAIL);
        if (userAuth == null) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }

        // TODO ????????? ????????? ?????????

        try {
            userAuthRepository.save(userAuth);
        } catch (DataIntegrityViolationException e) {
            if (JpaUtil.isDuplicateKeyException(e)) {
                throw ResponseError.BadRequest.ALREADY_EXISTS_EMAIL.getResponseException();
            }
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        } catch (Exception e) {
            throw ResponseError.BadRequest.ALREADY_EXISTS_EMAIL.getResponseException();
        }

        if (userAuth.getSeq() == null) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }
    }


    private void authResetEmail(EmailAuth emailAuth) {

        UserAuth userAuth = userAuthRepository.findByUserAndType(emailAuth.getUser(), UserAuthType.EMAIL);
        if (userAuth == null) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }

        userAuth.setId(emailAuth.getId());

        try {
            userAuthRepository.save(userAuth);

            User user = userAuth.getUser();
            user.setEmail(userAuth.getId());
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            if (JpaUtil.isDuplicateKeyException(e)) {
                throw ResponseError.BadRequest.ALREADY_EXISTS_EMAIL.getResponseException();
            }
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        } catch (Exception e) {
            throw ResponseError.BadRequest.ALREADY_EXISTS_EMAIL.getResponseException();
        }

        if (userAuth.getSeq() == null) {
            throw ResponseError.InternalServerError.UNEXPECTED_ERROR.getResponseException();
        }
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
