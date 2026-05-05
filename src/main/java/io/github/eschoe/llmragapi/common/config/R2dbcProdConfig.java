package io.github.eschoe.llmragapi.common.config;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.net.URI;

import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

/**
 * Heroku Postgres 의 DATABASE_URL 환경변수를 R2DBC ConnectionFactory 로 변환.
 *
 * Heroku 형식:  postgres://user:pass@host:port/dbname
 * R2DBC 형식:   r2dbc:postgresql://host:port/dbname  (+ 별도 user/password 옵션)
 *
 * Heroku Postgres 는 SSL 강제 → sslMode=require 옵션 추가.
 * Self-signed 인증서를 그대로 신뢰하기 위해 sslMode=require (verify-ca/full 아님).
 *
 * prod 프로필에서만 활성화. local 은 application.yaml 의 spring.r2dbc.* 사용.
 * application-prod.yaml 에서 spring.r2dbc.url 을 빈 값으로 두어 자동설정을 비활성화하고
 * 이 빈이 @Primary 로 ConnectionFactory 를 제공한다.
 */
@Configuration
@Profile("prod")
public class R2dbcProdConfig {

    private static final Logger log = LoggerFactory.getLogger(R2dbcProdConfig.class);

    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @Primary
    @Bean
    public ConnectionFactory connectionFactory() {
        if (databaseUrl == null || databaseUrl.isBlank()) {
            throw new IllegalStateException(
                    "DATABASE_URL 환경변수가 비어 있음. Heroku Postgres 애드온 또는 외부 DB connection URL 을 등록하세요.");
        }

        URI uri = URI.create(databaseUrl);

        String userInfo = uri.getUserInfo();
        if (userInfo == null || !userInfo.contains(":")) {
            throw new IllegalStateException(
                    "DATABASE_URL 에 user:password 정보가 없음 (postgres://user:pass@host:port/db 형식 필요).");
        }
        String[] auth = userInfo.split(":", 2);
        String user = auth[0];
        String password = auth[1];

        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");

        log.info("[R2DBC-PROD] connecting to {}:{}/{} (user={}, sslMode=require)", host, port, database, user);

        ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, host)
                .option(ConnectionFactoryOptions.PORT, port)
                .option(ConnectionFactoryOptions.DATABASE, database)
                .option(USER, user)
                .option(PASSWORD, password)
                .option(ConnectionFactoryOptions.SSL, true)
                .option(io.r2dbc.spi.Option.valueOf("sslMode"), "require")
                .build();

        return ConnectionFactories.get(options);
    }
}
