package com.mozi.moziserver.httpException;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

public interface ResponseError {

    //400: Bad request
    @RequiredArgsConstructor
    enum BadRequest {
        METHOD_ARGUMENT_NOT_VALID(""),
        BAD_REQUEST("bad request"),
        INVALID_EMAIL("invalid email"),
        ALREADY_EXISTS_EMAIL("already exists email"),
        INVALID_EMAIL_OR_PASSWORD("invalid email or password"),
        INVALID_SEQ("invalid seq"),
        ALREADY_CREATED( "already created"),
        ALREADY_DELETED("already deleted");

//        BAD_REQUEST(HttpStatus.BAD_REQUEST, "bad request"),
//        INVALID_ID(HttpStatus.BAD_REQUEST, "invalid id"),
//        INVALID_SEQ(HttpStatus.BAD_REQUEST, "invalid seq"),
//        EXISTS_ID(HttpStatus.BAD_REQUEST, "exists email"),
//        DUPLICATE_USER_NICK(HttpStatus.BAD_REQUEST, "user nick exists"),
//        INVALID_QUERY(HttpStatus.BAD_REQUEST, "invalid query"),
//        INVALID_PAGE(HttpStatus.BAD_REQUEST, "invalid page"),
//        INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "invalid pageSize"),
//        INVALID_PREV_LAST_SEQ(HttpStatus.BAD_REQUEST, "invalid prev last seq"),
//        ALREADY_FRIEND(HttpStatus.BAD_REQUEST, "already we are friend"),
//        ALREADY_ACCEPTED(HttpStatus.BAD_REQUEST, "already accepted"),
//        ALREADY_DENIED(HttpStatus.BAD_REQUEST, "already denied"),
//        ALREADY_CANCELED(HttpStatus.BAD_REQUEST, "already canceled"),
//        ALREADY_CREATED(HttpStatus.BAD_REQUEST, "already created"),
//        ALREADY_DELETED(HttpStatus.BAD_REQUEST, "already deleted"),
//        INVALID_TITLE(HttpStatus.BAD_REQUEST, "invalid title"),
//        INVALID_CONTENT(HttpStatus.BAD_REQUEST, "invalid content"),
//        INVALID_WANTED_DT(HttpStatus.BAD_REQUEST, "invalid wanted dt"),
//        INVALID_FRIEND_SEQ(HttpStatus.BAD_REQUEST, "invalid friend seq"),
//        INVALID_GROUP_SEQ(HttpStatus.BAD_REQUEST, "invalid group seq"),
//        INVALID_PLACE_NAME(HttpStatus.BAD_REQUEST, "invalid place name"),
//        INVALID_PICTURE_URL(HttpStatus.BAD_REQUEST, "invalid picture url"),
//        INVALID_PICTURE_URL_PREFIX(HttpStatus.BAD_REQUEST, "invalid picture url prefix"),
//        NOT_EXISTS_PICTURE_URL(HttpStatus.BAD_REQUEST, "not exists picture url"),
//        INVALID_GROUP_MEMBER_SEQ(HttpStatus.BAD_REQUEST, "invalid group member seq (not friend)"),
//        INVALID_KING_USER_SEQ(HttpStatus.BAD_REQUEST, "invalid king user seq (not friend)"),
//
//        UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "need to login"),
//
//        FORBIDDEN(HttpStatus.FORBIDDEN, "need to set nick"),
//        NO_AUTHORITY(HttpStatus.FORBIDDEN, "no authority"),
//        ACCESS_DENIED_DIARY(HttpStatus.FORBIDDEN, "access denied diary"),
//        PERMISSION_DENIED_DIARY(HttpStatus.FORBIDDEN, "permission denied diary"),
//        ACCESS_DENIED_GROUP(HttpStatus.FORBIDDEN, "access denied group"),
//        PERMISSION_DENIED_GROUP(HttpStatus.FORBIDDEN, "permission denied group"),
//        LEAVE_DENIED_GROUP(HttpStatus.FORBIDDEN, "leave denied group (king)"),
//
//        USER_NOT_EXISTS(HttpStatus.NOT_FOUND, "user not exists"),
//        FRIEND_NOT_EXISTS(HttpStatus.NOT_FOUND, "friend not exists"),
//        DIARY_NOT_EXISTS(HttpStatus.NOT_FOUND, "diary not exists"),
//        GROUP_NOT_EXISTS(HttpStatus.NOT_FOUND, "group not exists"),
//        GROUP_MEMBER_NOT_EXISTS(HttpStatus.NOT_FOUND, "group member not exists"),
//
//        ALREADY_UPDATED(HttpStatus.CONFLICT, "already update"),
//        ALREADY_GROUP_MEMBER(HttpStatus.CONFLICT, "already group member"),
//
//        EXTERNAL_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "external api error"),
//        TODO_CODE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "critical error"),
//        UNEXPECTED_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "unexcepected error");

        private final String message;

        public ResponseException getResponseException() {
            return getResponseException(null);
        }

        public ResponseException getResponseException(String appendMessage) {
            return new ResponseException(HttpStatus.BAD_REQUEST, name(),
                    appendMessage != null ? message + "(" + appendMessage + ")" : message);
        }
    }

    //401: Need to login
    @RequiredArgsConstructor
    enum Unauthorized {
        UNAUTHORIZED("need to login");

        private final String message;

        public ResponseException getResponseException() {
            return getResponseException(null);
        }

        public ResponseException getResponseException(String appendMessage) {
            return new ResponseException(HttpStatus.UNAUTHORIZED, name(),
                    appendMessage != null ? message + "(" + appendMessage + ")" : message);
        }
    }

    //403: Be authenticated but has not authorization
    @RequiredArgsConstructor
    enum Forbidden {
        NO_AUTHORITY("no authority"),
        NEED_NICK("need to set nick");

        private final String message;

        public ResponseException getResponseException() {
            return getResponseException(null);
        }

        public ResponseException getResponseException(String appendMessage) {
            return new ResponseException(HttpStatus.FORBIDDEN, name(),
                    appendMessage != null ? message + "(" + appendMessage + ")" : message);
        }
    }

    //404
    @RequiredArgsConstructor
    enum NotFound {
        USER_NOT_EXISTS("user not exists"),
        USER_CHALLENGE_NOT_EXISTS("user-challenge not exists");

        private final String message;

        public ResponseException getResponseException() {
            return getResponseException(null);
        }

        public ResponseException getResponseException(String appendMessage) {
            return new ResponseException(HttpStatus.NOT_FOUND, name(),
                    appendMessage != null ? message + "(" + appendMessage + ")" : message);
        }
    }

    //409: Simultaneous access
    @RequiredArgsConstructor
    enum Conflict {
        ALREADY_UPDATED("already update");

        private final String message;

        public ResponseException getResponseException() {
            return getResponseException(null);
        }

        public ResponseException getResponseException(String appendMessage) {
            return new ResponseException(HttpStatus.CONFLICT, name(),
                    appendMessage != null ? message + "(" + appendMessage + ")" : message);
        }
    }

    //500: Server problem
    @RequiredArgsConstructor
    enum InternalServerError {
        UNEXPECTED_ERROR("unexcepected error");

        private final String message;

        public ResponseException getResponseException() {
            return getResponseException(null);
        }

        public ResponseException getResponseException(String appendMessage) {
            return new ResponseException(HttpStatus.INTERNAL_SERVER_ERROR, name(),
                    appendMessage != null ? message + "(" + appendMessage + ")" : message);
        }
    }
}