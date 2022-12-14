package com.mozi.moziserver.model.entity;

import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
@Entity(name = "confirm")
public class Confirm extends AbstractTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seq;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_seq")
    User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_seq")
    Challenge challenge;

    @Column(name="img_url")
    private String imgUrl;

    @Column(name="declaration_state")
    private Byte declarationState;

    @Builder.Default
    private Integer likeCnt = 0;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name="confirm_seq")
    private List<ConfirmSticker> confirmStickerList;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name="confirm_seq")
    private List<Declaration> DeclarationList;

    @Builder.Default
    private Integer declarationCnt = 0;

    @Transient
    boolean isLiked;

    @Transient
    boolean isDeclared;
}
