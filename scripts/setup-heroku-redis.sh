#!/bin/bash

# Heroku Redis Writer/Reader 설정 스크립트
# 사용법: ./scripts/setup-heroku-redis.sh your-app-name

APP_NAME=$1

if [ -z "$APP_NAME" ]; then
    echo "사용법: $0 <app-name>"
    exit 1
fi

echo "🚀 Heroku Redis 설정을 시작합니다: $APP_NAME"

# Redis Premium 플랜 추가
echo "📦 Redis Premium 플랜을 추가합니다..."
heroku addons:create heroku-redis:premium-0 -a $APP_NAME

# 환경 변수 자동 설정
echo "⚙️  환경 변수를 설정합니다..."

# Redis URL에서 정보 추출
REDIS_URL=$(heroku config:get REDIS_URL -a $APP_NAME)
if [ -z "$REDIS_URL" ]; then
    echo "❌ REDIS_URL을 찾을 수 없습니다. Redis 애드온이 제대로 설치되었는지 확인하세요."
    exit 1
fi

# Redis URL 파싱 (redis://:password@host:port)
REDIS_INFO=$(echo $REDIS_URL | sed 's/redis:\/\/:\([^@]*\)@\([^:]*\):\([0-9]*\)/\1|\2|\3/')
REDIS_PASSWORD=$(echo $REDIS_INFO | cut -d'|' -f1)
REDIS_HOST=$(echo $REDIS_INFO | cut -d'|' -f2)
REDIS_PORT=$(echo $REDIS_INFO | cut -d'|' -f3)

echo "🔍 Redis 정보:"
echo "  Host: $REDIS_HOST"
echo "  Port: $REDIS_PORT"
echo "  Password: ${REDIS_PASSWORD:0:10}..."

# Writer 설정 (마스터)
heroku config:set REDIS_WRITER_HOST=$REDIS_HOST -a $APP_NAME
heroku config:set REDIS_WRITER_PORT=$REDIS_PORT -a $APP_NAME
heroku config:set REDIS_WRITER_PASSWORD=$REDIS_PASSWORD -a $APP_NAME
heroku config:set REDIS_WRITER_USERNAME=default -a $APP_NAME

# Reader 설정 (Premium 플랜에서는 복제본 자동 제공)
# 실제 운영에서는 복제본 URL을 사용해야 하지만, 
# Premium 플랜에서는 같은 URL을 사용해도 자동으로 읽기 전용 복제본으로 라우팅됩니다.
heroku config:set REDIS_READER_HOST=$REDIS_HOST -a $APP_NAME
heroku config:set REDIS_READER_PORT=$REDIS_PORT -a $APP_NAME
heroku config:set REDIS_READER_PASSWORD=$REDIS_PASSWORD -a $APP_NAME
heroku config:set REDIS_READER_USERNAME=default -a $APP_NAME

echo "✅ Redis Writer/Reader 설정이 완료되었습니다!"

# 설정 확인
echo "🔍 설정된 환경 변수:"
heroku config -a $APP_NAME | grep REDIS

echo ""
echo "📋 다음 단계:"
echo "1. 애플리케이션을 재배포하세요: git push heroku main"
echo "2. 로그를 확인하세요: heroku logs --tail -a $APP_NAME"
echo "3. Redis 연결을 테스트하세요: heroku run java -jar target/llm-rag-api.jar -Dspring.profiles.active=test -a $APP_NAME"

