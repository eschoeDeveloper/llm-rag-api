#!/bin/bash

# Redis 연결 테스트 스크립트
# 사용법: ./scripts/test-redis-connection.sh your-app-name

APP_NAME=$1

if [ -z "$APP_NAME" ]; then
    echo "사용법: $0 <app-name>"
    exit 1
fi

echo "🔍 Redis 연결을 테스트합니다: $APP_NAME"

# 환경 변수 확인
echo "📋 Redis 환경 변수:"
heroku config -a $APP_NAME | grep REDIS

echo ""
echo "🧪 Redis 연결 테스트를 실행합니다..."

# Redis 연결 테스트
heroku run -a $APP_NAME bash -c "
echo 'Testing Redis Writer connection...'
java -cp target/llm-rag-api.jar -Dspring.profiles.active=heroku \
  -Dspring.data.redis.writer.host=\$REDIS_WRITER_HOST \
  -Dspring.data.redis.writer.port=\$REDIS_WRITER_PORT \
  -Dspring.data.redis.writer.password=\$REDIS_WRITER_PASSWORD \
  io.github.eschoe.llmragapi.util.RedisConnectionTest

echo 'Testing Redis Reader connection...'
java -cp target/llm-rag-api.jar -Dspring.profiles.active=heroku \
  -Dspring.data.redis.reader.host=\$REDIS_READER_HOST \
  -Dspring.data.redis.reader.port=\$REDIS_READER_PORT \
  -Dspring.data.redis.reader.password=\$REDIS_READER_PASSWORD \
  io.github.eschoe.llmragapi.util.RedisConnectionTest
"

echo "✅ Redis 연결 테스트가 완료되었습니다!"

