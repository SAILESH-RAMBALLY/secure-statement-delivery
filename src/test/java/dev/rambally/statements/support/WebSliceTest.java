package dev.rambally.statements.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import dev.rambally.statements.bootstrap.DevJwtConfig;
import dev.rambally.statements.bootstrap.SecurityConfig;
import dev.rambally.statements.bootstrap.WebConfig;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ActiveProfiles;

/**
 * Web slice with the real security chains, the real argument resolvers and exception handler, and the
 * test-profile RSA decoder. Use cases are supplied per test as tiny fakes, never mocks.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest
@Import({SecurityConfig.class, WebConfig.class, DevJwtConfig.class, WebSliceTestConfig.class})
@ActiveProfiles("test")
public @interface WebSliceTest {

    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] controllers() default {};
}
