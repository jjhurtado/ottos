package org.jobits.ottos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OttosApplication {

    public static void main(String[] args) {
        SpringApplication.run(OttosApplication.class, args);
    }
}
