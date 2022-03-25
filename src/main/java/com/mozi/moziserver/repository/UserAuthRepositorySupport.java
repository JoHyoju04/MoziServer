package com.mozi.moziserver.repository;

import com.mozi.moziserver.model.entity.User;
import com.mozi.moziserver.model.entity.UserAuth;

public interface UserAuthRepositorySupport {
    UserAuth findUserEmailByNickName(String nickName);

    //String checkQuitUser(String id);

    User findUserSeqByEmail(String email);

    UserAuth findByUserSeqAndPw(User user);
}
