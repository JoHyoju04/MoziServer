package com.mozi.moziserver.model.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

/**
 * uniq(deviceId, token)
 * 멀티 로그인 가능
 */
@Getter
@Setter
@Entity(name = "user_fcm")
public class UserFcm extends AbstractTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seq;

    private String deviceId;

    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_seq")
    private User user;
}


