package dev.rambally.statements;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationSmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void context_loads_with_test_profile() {
        assertThat(context).isNotNull();
        assertThat(context.getEnvironment().getActiveProfiles()).contains("test");
    }
}
