package com.mozi.moziserver.repository;

import com.mozi.moziserver.model.entity.*;
import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class ConfirmStickerRepositoryImpl extends QuerydslRepositorySupport implements ConfirmStickerRepositorySupport{

    private final QConfirmSticker qConfirmSticker=QConfirmSticker.confirmSticker;

    public ConfirmStickerRepositoryImpl() {super(ConfirmSticker.class);}

    @Override
    public List<ConfirmSticker> findAllBySeq(Long seq){

        List<ConfirmSticker> confirmStickerList=from(qConfirmSticker)
                .where(qConfirmSticker.confirm.seq.in(seq))
                .fetch();

        return confirmStickerList;

    }

    @Override
    public Boolean findByUserAndConfirmSeq(Long userSeq, Long confirmSeq){
        Boolean ret=true;
        ConfirmSticker confirmSticker=from(qConfirmSticker)
                .where(qConfirmSticker.confirm.seq.in(confirmSeq),
                        qConfirmSticker.user.seq.in(userSeq))
                .fetchOne();
        if(confirmSticker==null)    ret=false;

        return ret;
    }
}
