package com.mgmtp.gives;

import com.mgmtp.gives.support.PostgreSqlTestContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgreSqlTestContainerConfiguration.class)
class MgmGivesApplicationTests {

    @Test
    void contextLoads() {
    }
}
