package io.github.eschoe.llmragapi.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
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

    // LlmCacheImplService에서 사용하는 redisWriterTemplate 빈 추가
    @Bean("redisWriterTemplate")
    public ReactiveStringRedisTemplate redisWriterTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveStringRedisTemplate(connectionFactory);
    }

    // LlmCacheImplService에서 사용하는 redisReaderTemplate 빈 추가
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

    private RedisStandaloneConfiguration parseRedisUrl(String redisUrl) {
        System.out.println("DEBUG: Parsing Redis URL: " + redisUrl);
        
        // redis://:password@host:port 또는 redis://username:password@host:port 형식 파싱
        String url = redisUrl.replace("redis://", "").replace("rediss://", "");
        String[] parts = url.split("@");
        
        System.out.println("DEBUG: URL parts length: " + parts.length);
        for (int i = 0; i < parts.length; i++) {
            System.out.println("DEBUG: Part " + i + ": " + parts[i]);
        }
        
        if (parts.length == 2) {
            String authPart = parts[0];
            String[] hostParts = parts[1].split(":");
            
            String host = hostParts[0];
            int port = hostParts.length > 1 ? Integer.parseInt(hostParts[1]) : 6379;
            
            System.out.println("DEBUG: Host: " + host + ", Port: " + port);
            System.out.println("DEBUG: Auth part: " + authPart);
            
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
            
            // authPart가 ":password" 형식인지 확인
            if (authPart.startsWith(":")) {
                String password = authPart.substring(1); // ":" 제거하고 비밀번호만 추출
                // URL 디코딩 적용
                try {
                    password = java.net.URLDecoder.decode(password, "UTF-8");
                } catch (Exception e) {
                    System.out.println("DEBUG: URL decoding failed: " + e.getMessage());
                }
                System.out.println("DEBUG: Extracted password: " + password);
                if (!password.isEmpty()) {
                    config.setPassword(RedisPassword.of(password));
                    System.out.println("DEBUG: Password set successfully");
                }
            } else {
                // username:password 형식인 경우
                String[] authParts = authPart.split(":");
                if (authParts.length > 1) {
                    String password = authParts[1];
                    // URL 디코딩 적용
                    try {
                        password = java.net.URLDecoder.decode(password, "UTF-8");
                    } catch (Exception e) {
                        System.out.println("DEBUG: URL decoding failed: " + e.getMessage());
                    }
                    System.out.println("DEBUG: Username/password format, password: " + password);
                    if (!password.isEmpty()) {
                        config.setPassword(RedisPassword.of(password));
                        System.out.println("DEBUG: Password set successfully");
                    }
                }
            }
            
            return config;
        }
        
        System.out.println("DEBUG: Failed to parse URL, using default configuration");
        // 파싱 실패 시 기본값
        return new RedisStandaloneConfiguration("localhost", 6379);
    }
}
