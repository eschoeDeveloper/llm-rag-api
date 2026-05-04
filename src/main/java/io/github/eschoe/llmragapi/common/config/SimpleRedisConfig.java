package io.github.eschoe.llmragapi.common.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
public class SimpleRedisConfig {

    private static final Logger log = LoggerFactory.getLogger(SimpleRedisConfig.class);

    @Value("${spring.data.redis.url:}")
    private String redisUrl;

    @Value("${spring.data.redis.writer.host:}")
    private String writerHost;

    @Value("${spring.data.redis.writer.port:6379}")
    private int writerPort;

    @Value("${spring.data.redis.writer.password:}")
    private String writerPassword;

    @Primary
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config;
        
        // REDIS_URL이 있으면 파싱해서 사용
        if (redisUrl != null && !redisUrl.isEmpty()) {
            config = parseRedisUrl(redisUrl);
            config.setPassword(RedisPassword.of(writerPassword));
        } else {
            // 환경 변수에서 직접 설정
            config = new RedisStandaloneConfiguration(writerHost, writerPort);
            if (writerPassword != null && !writerPassword.isEmpty()) {
                config.setPassword(RedisPassword.of(writerPassword));
            }
        }

        var socket = SocketOptions.builder()
                .connectTimeout(Duration.ofSeconds(30))  // 연결 타임아웃 증가
                .keepAlive(true)  // Keep-Alive 활성화
                .build();

        LettuceClientConfiguration clientCfg;
        
        if (redisUrl != null && redisUrl.startsWith("rediss://")) {
            // Heroku Redis는 자체 서명된 인증서를 사용하므로 인증서 검증 비활성화
            io.lettuce.core.SslOptions sslOptions = io.lettuce.core.SslOptions.builder()
                    .jdkSslProvider()
                    .build();
            
            io.lettuce.core.ClientOptions sslClientOptions = ClientOptions.builder()
                    .socketOptions(socket)
                    .sslOptions(sslOptions)
                    .autoReconnect(true)
                    .build();
            
            clientCfg = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofSeconds(30))
                    .shutdownTimeout(Duration.ofSeconds(5))
                    .clientOptions(sslClientOptions)
                    .useSsl()
                    .disablePeerVerification()  // 인증서 검증 비활성화
                    .build();
        } else {
            var clientOptions = ClientOptions.builder()
                    .socketOptions(socket)
                    .autoReconnect(true)
                    .build();
            
            clientCfg = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofSeconds(30))
                    .shutdownTimeout(Duration.ofSeconds(5))
                    .clientOptions(clientOptions)
                    .build();
        }

        return new LettuceConnectionFactory(config, clientCfg);
    }

    @Primary
    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveStringRedisTemplate(connectionFactory);
    }

    // LlmCacheService에서 사용하는 redisWriterTemplate 빈 추가
    @Bean("redisWriterTemplate")
    public ReactiveStringRedisTemplate redisWriterTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveStringRedisTemplate(connectionFactory);
    }

    // LlmCacheService에서 사용하는 redisReaderTemplate 빈 추가
    @Bean("redisReaderTemplate")
    public ReactiveStringRedisTemplate redisReaderTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveStringRedisTemplate(connectionFactory);
    }

    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveRedisTemplate<>(connectionFactory, RedisSerializationContext.string());
    }

    /**
     * redis://[:password@]host:port 또는 redis://user:password@host:port 형식의 URL 파싱.
     * 비밀번호는 URL-encoded 일 수 있어 디코딩 거침. 형식이 안 맞으면 localhost:6379 fallback.
     */
    private RedisStandaloneConfiguration parseRedisUrl(String redisUrl) {
        log.debug("Parsing Redis URL");

        String url = redisUrl.replace("redis://", "").replace("rediss://", "");
        String[] parts = url.split("@");

        if (parts.length == 2) {
            String authPart = parts[0];
            String[] hostParts = parts[1].split(":");

            String host = hostParts[0];
            int port = hostParts.length > 1 ? Integer.parseInt(hostParts[1]) : 6379;
            log.debug("Redis host={}, port={}", host, port);

            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);

            String password = extractPassword(authPart);
            if (password != null && !password.isEmpty()) {
                config.setPassword(RedisPassword.of(password));
            }
            return config;
        }

        log.warn("Failed to parse Redis URL — falling back to localhost:6379");
        return new RedisStandaloneConfiguration("localhost", 6379);
    }

    /** authPart 가 ":password" 또는 "user:password" 형식이라 가정하고 password 만 추출 + URL 디코딩. */
    private String extractPassword(String authPart) {
        String raw;
        if (authPart.startsWith(":")) {
            raw = authPart.substring(1);
        } else {
            String[] split = authPart.split(":");
            if (split.length < 2) return null;
            raw = split[1];
        }
        try {
            return java.net.URLDecoder.decode(raw, "UTF-8");
        } catch (Exception e) {
            log.warn("Redis password URL decoding failed: {}", e.getMessage());
            return raw;
        }
    }
}
