package dev.rambally.statements.adapters.in.web;

import dev.rambally.statements.application.Principal;
import dev.rambally.statements.domain.CustomerId;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Turns the verified JWT into the application's {@link Principal}: identity is the subject, admin-ness
 * is the ROLE_ADMIN authority. This is the only place a caller identity is ever constructed.
 */
public class JwtPrincipalResolver implements HandlerMethodArgumentResolver {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Principal.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return toPrincipal(SecurityContextHolder.getContext().getAuthentication());
    }

    Principal toPrincipal(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new InvalidPrincipalException("request is not authenticated with a JWT");
        }
        String subject = jwtAuth.getToken().getSubject();
        CustomerId customerId;
        try {
            customerId = new CustomerId(subject);
        } catch (IllegalArgumentException e) {
            throw new InvalidPrincipalException("JWT subject is not a usable customer id");
        }
        boolean admin = jwtAuth.getAuthorities().stream().anyMatch(a -> ADMIN_AUTHORITY.equals(a.getAuthority()));
        return new Principal(customerId, admin);
    }
}
