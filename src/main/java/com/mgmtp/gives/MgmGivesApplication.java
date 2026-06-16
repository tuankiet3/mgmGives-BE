package com.mgmtp.gives;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
public class MgmGivesApplication {

    public static void main(String[] args) {
        SpringApplication.run(MgmGivesApplication.class, args);
    }

}
