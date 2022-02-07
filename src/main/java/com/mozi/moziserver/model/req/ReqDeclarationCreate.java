package com.mozi.moziserver.model.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mozi.moziserver.model.mappedenum.DeclarationType;
import lombok.Getter;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotNull;
import java.util.Date;

@Getter
public class ReqDeclarationCreate {
    @NotNull
    private Long seq;

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING,pattern="yyyy-MM-dd HH:mm:ss")
    private Date date;

    @NotNull
    private DeclarationType type;
}
