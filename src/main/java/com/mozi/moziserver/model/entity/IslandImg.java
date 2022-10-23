package com.mozi.moziserver.model.entity;

import lombok.*;

import javax.persistence.*;
import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity(name = "island_img")
public class IslandImg extends AbstractTimeEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seq;

    private Integer type;

    private Integer level;

    private String imgUrl;

    private String thumbnailImgUrl;
}
