package io.github.eschoe.llmragapi.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.SocketOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration  // 단순한 Redis 설정을 위해 비활성화
public class ReactiveRedisConfig {

    // ===== Writer (MASTER) =====
    @Value("${spring.data.redis.writer.host}")
    private String writerHost;

    @Value("${spring.data.redis.writer.port}")
    private int writerPort;

    @Value("${spring.data.redis.writer.username}")   // ACL 미사용이면 공란
    private String writerUser;

    @Value("${spring.data.redis.writer.password}")   // requirepass/ACL
    private String writerPass;

    // ===== Reader (REPLICA) =====
    @Value("${spring.data.redis.reader.host}")       // 레플리카 없으면 비워두세요
    private String readerHost;

    @Value("${spring.data.redis.reader.port}")
    private int readerPort;

    @Value("${spring.data.redis.reader.username}")
    private String readerUser;

    @Value("${spring.data.redis.reader.password}")
    private String readerPass;

    // ===== 공통 타임아웃 =====
    @Value("${spring.data.redis.connect-timeout}")
    private int connectTimeoutSec;

    @Value("${spring.data.redis.command-timeout}")
    private int commandTimeoutSec;

    // ===========================
    // Writer(마스터) 전용 Factory/Template
    // ===========================
    @Primary
    @Bean("redisWriterFactory")
    public LettuceConnectionFactory redisWriterFactory() {
        var conf = new RedisStandaloneConfiguration(writerHost, writerPort);
        if (!writerUser.isBlank()) conf.setUsername(writerUser);
        if (!writerPass.isBlank()) conf.setPassword(RedisPassword.of(writerPass));

        var socket = SocketOptions.builder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSec)).build();

        var clientOptions = ClientOptions.builder()
                .socketOptions(socket)
                .build();

        var clientCfg = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(Duration.ofSeconds(commandTimeoutSec))
                .useSsl() // SSL 연결 활성화
                .build();

        return new LettuceConnectionFactory(conf, clientCfg);
    }

    @Primary
    @Bean("redisWriterTemplate")
    public ReactiveStringRedisTemplate redisWriterTemplate(
            @Qualifier("redisWriterFactory") LettuceConnectionFactory f) {
        return new ReactiveStringRedisTemplate(f);
    }

    // ===========================
    // Reader(레플리카) 전용 Factory/Template (선택)
    // ===========================
    @Bean("redisReaderFactory")
    public LettuceConnectionFactory redisReaderFactory() {
        // 레플리카 미지정 시, 안전하게 마스터로 fallback (원치 않으면 빈 등록 제거)
        String host = readerHost.isBlank() ? writerHost : readerHost;
        int    port = (readerHost.isBlank() ? writerPort : readerPort);

        var conf = new RedisStandaloneConfiguration(host, port);
        if (!readerUser.isBlank()) conf.setUsername(readerUser);
        if (!readerPass.isBlank()) conf.setPassword(RedisPassword.of(readerPass));

        var socket = SocketOptions.builder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSec)).build();

        var clientOptions = ClientOptions.builder()
                .socketOptions(socket)
                .build();

        var clientCfg = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(Duration.ofSeconds(commandTimeoutSec))
                .readFrom(ReadFrom.REPLICA_PREFERRED) // GET 등 읽기는 레플리카 우선
                .useSsl() // SSL 연결 활성화
                .build();

        return new LettuceConnectionFactory(conf, clientCfg);
    }

    @Bean("redisReaderTemplate")
    public ReactiveStringRedisTemplate redisReaderTemplate(
            @Qualifier("redisReaderFactory") LettuceConnectionFactory f) {
        return new ReactiveStringRedisTemplate(f);
    }

    // 필요 시 제네릭 템플릿도 제공
    @Bean("redisWriterJsonTemplate")
    public ReactiveRedisTemplate<String, String> redisWriterJsonTemplate(
            @Qualifier("redisWriterFactory") LettuceConnectionFactory f) {
        return new ReactiveRedisTemplate<>(f, RedisSerializationContext.string());
    }

    @Bean("redisReaderJsonTemplate")
    public ReactiveRedisTemplate<String, String> redisReaderJsonTemplate(
            @Qualifier("redisReaderFactory") LettuceConnectionFactory f) {
        return new ReactiveRedisTemplate<>(f, RedisSerializationContext.string());
    }

}
