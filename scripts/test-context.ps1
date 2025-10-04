# 대화 맥락 유지 테스트 스크립트
# 사용법: .\scripts\test-context.ps1

param(
    [Parameter(Mandatory=$false)]
    [string]$BaseUrl = "http://localhost:8080"
)

# 한글 인코딩 설정
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "🧪 대화 맥락 유지 테스트를 시작합니다..." -ForegroundColor Green
Write-Host "Base URL: $BaseUrl" -ForegroundColor Cyan

# 세션 ID 저장용 변수
$sessionId = $null

# 첫 번째 메시지
Write-Host "`n📝 첫 번째 메시지 전송..." -ForegroundColor Yellow
$firstMessage = @{
    query = "안녕하세요! 제 이름은 홍길동입니다."
    config = @{
        topK = 5
        threshold = 0.7
    }
} | ConvertTo-Json

try {
    $response1 = Invoke-RestMethod -Uri "$BaseUrl/api/chat" -Method POST -Body $firstMessage -ContentType "application/json"
    $sessionId = $response1.sessionId
    Write-Host "✅ 첫 번째 응답: $($response1.answer)" -ForegroundColor Green
    Write-Host "🔑 세션 ID: $sessionId" -ForegroundColor Cyan
} catch {
    Write-Host "❌ 첫 번째 요청 실패: $($_.Exception.Message)" -ForegroundColor Red
    return
}

# 두 번째 메시지 (세션 ID 포함)
Write-Host "`n📝 두 번째 메시지 전송 (세션 ID 포함)..." -ForegroundColor Yellow
$secondMessage = @{
    query = "제 이름이 뭐라고 했죠?"
    sessionId = $sessionId
    config = @{
        topK = 5
        threshold = 0.7
    }
} | ConvertTo-Json

try {
    $response2 = Invoke-RestMethod -Uri "$BaseUrl/api/chat" -Method POST -Body $secondMessage -ContentType "application/json"
    Write-Host "✅ 두 번째 응답: $($response2.answer)" -ForegroundColor Green
} catch {
    Write-Host "❌ 두 번째 요청 실패: $($_.Exception.Message)" -ForegroundColor Red
}

# 세 번째 메시지 (헤더로 세션 ID 전달)
Write-Host "`n📝 세 번째 메시지 전송 (헤더로 세션 ID)..." -ForegroundColor Yellow
$thirdMessage = @{
    query = "이전에 말한 내용을 기억하고 있나요?"
    config = @{
        topK = 5
        threshold = 0.7
    }
} | ConvertTo-Json

$headers = @{
    "X-Session-ID" = $sessionId
    "Content-Type" = "application/json"
}

try {
    $response3 = Invoke-RestMethod -Uri "$BaseUrl/api/chat" -Method POST -Body $thirdMessage -Headers $headers
    Write-Host "✅ 세 번째 응답: $($response3.answer)" -ForegroundColor Green
} catch {
    Write-Host "❌ 세 번째 요청 실패: $($_.Exception.Message)" -ForegroundColor Red
}

# 히스토리 조회
Write-Host "`n📋 대화 히스토리 조회..." -ForegroundColor Yellow
try {
    $historyResponse = Invoke-RestMethod -Uri "$BaseUrl/api/history" -Method GET -Headers @{"X-Session-ID" = $sessionId}
    Write-Host "✅ 히스토리 조회 성공:" -ForegroundColor Green
    Write-Host $historyResponse.history -ForegroundColor White
} catch {
    Write-Host "❌ 히스토리 조회 실패: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n🎉 대화 맥락 유지 테스트가 완료되었습니다!" -ForegroundColor Green
