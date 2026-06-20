package com.volcanoartscenter.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = { SessionAutoConfiguration.class })
@EnableScheduling
public class VolcanoPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(VolcanoPlatformApplication.class, args);
    }
}
