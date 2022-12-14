package com.mozi.moziserver.model.res;

import com.mozi.moziserver.model.entity.Sticker;
import lombok.Getter;

@Getter
public class ResStickerList {
    private String imgUrl;
    private Long seq;

    private ResStickerList(Sticker sticker) {
        this.imgUrl=sticker.getImgUrl();
        this.seq=sticker.getSeq();
    }

    public static ResStickerList of(Sticker sticker) { return new ResStickerList(sticker); }
}
