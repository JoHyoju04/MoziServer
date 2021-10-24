package com.mozi.moziserver.model.mappedenum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum UserAuthType {
    ID(1),
    KAKAO(2);
//    FACEBOOK(3),
//    GOOGLE(4),
//    NAVER(5),

    @Getter
    private final int type;

    public static UserAuthType valueOf(int type) {
        for (UserAuthType userAuthType : values())
            if (userAuthType.getType() == type)
                return userAuthType;

        return null;
    }

    public boolean isSocial() {
        return this != ID;
    }
}