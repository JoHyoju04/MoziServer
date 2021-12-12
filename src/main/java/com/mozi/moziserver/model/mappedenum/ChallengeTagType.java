package com.mozi.moziserver.model.mappedenum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hibernate.cfg.Environment;

@Getter
@RequiredArgsConstructor
public enum ChallengeTagType {
    INDOOR("실내"),
    ENVIRONMENT("환경"),
    OUTDOOR("실외");
    
    private final String name;
}