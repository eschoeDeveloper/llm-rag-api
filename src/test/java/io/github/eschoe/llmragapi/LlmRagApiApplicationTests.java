package io.github.eschoe.llmragapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.config.import=classpath:application-test.yaml"
})
class LlmRagApiApplicationTests {

    @Test
    void contextLoads() {
    }

}
