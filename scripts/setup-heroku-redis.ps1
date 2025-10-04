# Heroku Redis Writer/Reader 설정 PowerShell 스크립트
# 사용법: .\scripts\setup-heroku-redis.ps1 your-app-name

param(
    [Parameter(Mandatory=$true)]
    [string]$AppName
)

# 한글 인코딩 설정
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "🚀 Heroku Redis 설정을 시작합니다: $AppName" -ForegroundColor Green

# Redis Premium 플랜 추가
Write-Host "📦 Redis Premium 플랜을 추가합니다..." -ForegroundColor Yellow
$AddonResult = heroku addons:create heroku-redis:premium-0 -a $AppName

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Redis 애드온 추가에 실패했습니다." -ForegroundColor Red
    exit 1
}

# Redis 애드온 생성 완료 대기
Write-Host "⏳ Redis 애드온 생성 완료를 기다립니다..." -ForegroundColor Yellow
$MaxWaitTime = 300 # 5분
$WaitTime = 0
$CheckInterval = 10 # 10초마다 확인

do {
    Start-Sleep -Seconds $CheckInterval
    $WaitTime += $CheckInterval
    
    $AddonInfo = heroku addons:info redis-tetrahedral-53466 -a $AppName 2>$null
    if ($AddonInfo -match "State:\s+available") {
        Write-Host "✅ Redis 애드온이 준비되었습니다!" -ForegroundColor Green
        break
    }
    
    Write-Host "⏳ Redis 애드온 생성 중... ($WaitTime/$MaxWaitTime 초)" -ForegroundColor Yellow
    
    if ($WaitTime -ge $MaxWaitTime) {
        Write-Host "❌ Redis 애드온 생성 시간이 초과되었습니다. 수동으로 확인해주세요." -ForegroundColor Red
        Write-Host "heroku addons:info redis-tetrahedral-53466 -a $AppName" -ForegroundColor Yellow
        exit 1
    }
} while ($true)

# 환경 변수 자동 설정
Write-Host "⚙️  환경 변수를 설정합니다..." -ForegroundColor Yellow

# Redis URL에서 정보 추출
$RedisUrl = heroku config:get REDIS_URL -a $AppName
if (-not $RedisUrl) {
    Write-Host "❌ REDIS_URL을 찾을 수 없습니다. Redis 애드온이 제대로 설치되었는지 확인하세요." -ForegroundColor Red
    exit 1
}

# Redis URL 파싱 (redis:// 또는 rediss:// 지원)
$RedisUrlPattern = "redis[s]?://:([^@]*)@([^:]*):([0-9]*)"
if ($RedisUrl -match $RedisUrlPattern) {
    $RedisPassword = $matches[1]
    $RedisHost = $matches[2]
    $RedisPort = $matches[3]
} else {
    Write-Host "❌ Redis URL 형식을 파싱할 수 없습니다: $RedisUrl" -ForegroundColor Red
    Write-Host "지원되는 형식: redis://:password@host:port 또는 rediss://:password@host:port" -ForegroundColor Yellow
    exit 1
}

Write-Host "🔍 Redis 정보:" -ForegroundColor Cyan
Write-Host "  Host: $RedisHost" -ForegroundColor White
Write-Host "  Port: $RedisPort" -ForegroundColor White
Write-Host "  Password: $($RedisPassword.Substring(0, [Math]::Min(10, $RedisPassword.Length)))..." -ForegroundColor White

# Writer 설정 (마스터)
Write-Host "📝 Writer 설정을 구성합니다..." -ForegroundColor Yellow
heroku config:set REDIS_WRITER_HOST=$RedisHost -a $AppName
heroku config:set REDIS_WRITER_PORT=$RedisPort -a $AppName
heroku config:set REDIS_WRITER_PASSWORD=$RedisPassword -a $AppName
heroku config:set REDIS_WRITER_USERNAME=default -a $AppName

# Reader 설정 (Premium 플랜에서는 복제본 자동 제공)
Write-Host "📖 Reader 설정을 구성합니다..." -ForegroundColor Yellow
heroku config:set REDIS_READER_HOST=$RedisHost -a $AppName
heroku config:set REDIS_READER_PORT=$RedisPort -a $AppName
heroku config:set REDIS_READER_PASSWORD=$RedisPassword -a $AppName
heroku config:set REDIS_READER_USERNAME=default -a $AppName

Write-Host "✅ Redis Writer/Reader 설정이 완료되었습니다!" -ForegroundColor Green

# 설정 확인
Write-Host "🔍 설정된 환경 변수:" -ForegroundColor Cyan
heroku config -a $AppName | Select-String "REDIS"

Write-Host ""
Write-Host "📋 다음 단계:" -ForegroundColor Yellow
Write-Host "1. 애플리케이션을 재배포하세요: git push heroku main" -ForegroundColor White
Write-Host "2. 로그를 확인하세요: heroku logs --tail -a $AppName" -ForegroundColor White
Write-Host "3. Redis 연결을 테스트하세요: .\scripts\test-redis-connection.ps1 $AppName" -ForegroundColor White
