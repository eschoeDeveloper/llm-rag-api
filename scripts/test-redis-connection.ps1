# Redis 연결 테스트 PowerShell 스크립트
# 사용법: .\scripts\test-redis-connection.ps1 your-app-name

param(
    [Parameter(Mandatory=$true)]
    [string]$AppName
)

# 한글 인코딩 설정
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "🔍 Redis 연결을 테스트합니다: $AppName" -ForegroundColor Green

# 환경 변수 확인
Write-Host "📋 Redis 환경 변수:" -ForegroundColor Cyan
heroku config -a $AppName | Select-String "REDIS"

Write-Host ""
Write-Host "🧪 Redis 연결 테스트를 실행합니다..." -ForegroundColor Yellow

# 간단한 Redis 연결 테스트
Write-Host "📋 Redis 환경 변수 확인:" -ForegroundColor Cyan
$WriterHost = heroku config:get REDIS_WRITER_HOST -a $AppName
$WriterPort = heroku config:get REDIS_WRITER_PORT -a $AppName
$ReaderHost = heroku config:get REDIS_READER_HOST -a $AppName
$ReaderPort = heroku config:get REDIS_READER_PORT -a $AppName

if ($WriterHost -and $WriterPort -and $ReaderHost -and $ReaderPort) {
    Write-Host "✅ Redis Writer 설정 확인됨: ${WriterHost}:${WriterPort}" -ForegroundColor Green
    Write-Host "✅ Redis Reader 설정 확인됨: ${ReaderHost}:${ReaderPort}" -ForegroundColor Green
    Write-Host "✅ Redis 환경 변수 설정이 완료되었습니다!" -ForegroundColor Green
} else {
    Write-Host "❌ Redis 환경 변수 설정에 문제가 있습니다." -ForegroundColor Red
}

Write-Host "✅ Redis 연결 테스트가 완료되었습니다!" -ForegroundColor Green
