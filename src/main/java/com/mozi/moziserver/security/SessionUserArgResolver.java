package com.mozi.moziserver.security;

import com.mozi.moziserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@RequiredArgsConstructor
public class SessionUserArgResolver implements HandlerMethodArgumentResolver {
    private final UserRepository userRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (parameter.getParameterAnnotation(SessionUser.class) == null) {
            return false;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated();
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        if (SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof ResUserSignIn) {
            ResUserSignIn res = (ResUserSignIn) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return res.getUserSeq();
        }
        return null;
    }

//    @Override
//    public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
//        return ((ResUserEmailSignIn) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUserSeq();
//    }
}