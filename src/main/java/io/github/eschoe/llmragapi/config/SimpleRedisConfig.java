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

    @Value("${REDIS_URL:}")
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
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        var clientOptions = ClientOptions.builder()
                .socketOptions(socket)
                .build();

        var clientCfgBuilder = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(Duration.ofSeconds(10));
        
        if (redisUrl != null && redisUrl.startsWith("rediss://")) {
            clientCfgBuilder.useSsl();
        }
        
        var clientCfg = clientCfgBuilder.build();

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

    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveRedisTemplate<>(connectionFactory, RedisSerializationContext.string());
    }

    private RedisStandaloneConfiguration parseRedisUrl(String redisUrl) {
        // rediss://:password@host:port 형식 파싱
        String url = redisUrl.replace("redis://", "").replace("rediss://", "");
        String[] parts = url.split("@");
        
        if (parts.length == 2) {
            String[] authParts = parts[0].split(":");
            String[] hostParts = parts[1].split(":");
            
            String password = authParts.length > 1 ? authParts[1] : "";
            String host = hostParts[0];
            int port = hostParts.length > 1 ? Integer.parseInt(hostParts[1]) : 6379;
            
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
            if (!password.isEmpty()) {
                config.setPassword(RedisPassword.of(password));
            }
            return config;
        }
        
        // 파싱 실패 시 기본값
        return new RedisStandaloneConfiguration("localhost", 6379);
    }
}
