package dev.rambally.statements;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("dev.rambally.statements.bootstrap")
public class SecureStatementDeliveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureStatementDeliveryApplication.class, args);
    }
}
