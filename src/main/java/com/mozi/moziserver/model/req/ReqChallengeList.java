package com.mozi.moziserver.model.req;

import com.mozi.moziserver.model.mappedenum.ChallengeTagType;
import com.mozi.moziserver.model.mappedenum.ChallengeThemeType;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Getter
@Setter
public class ReqChallengeList {

    private List<ChallengeTagType> challengeTagType;

    private List<Long> themeSeq;

    @Min(1L)
    private Long prevLastPostSeq;

    @Min(1L) @Max(50L)
    private Integer pageSize = 20;
}
