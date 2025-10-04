# 한글 인코딩 문제 해결 유틸리티 스크립트
# 사용법: .\scripts\fix-korean-encoding.ps1

# 한글 인코딩 설정 (강화된 버전)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$PSDefaultParameterValues['*:Encoding'] = 'utf8'

Write-Host "🔧 한글 인코딩 문제를 해결합니다..." -ForegroundColor Green

# 현재 인코딩 상태 확인
Write-Host "📋 현재 인코딩 상태:" -ForegroundColor Cyan
Write-Host "  Console Output Encoding: $([Console]::OutputEncoding.EncodingName)" -ForegroundColor White
Write-Host "  PowerShell Output Encoding: $($OutputEncoding.EncodingName)" -ForegroundColor White

# UTF-8로 설정
Write-Host "⚙️  UTF-8 인코딩으로 설정합니다..." -ForegroundColor Yellow
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# 설정 후 상태 확인
Write-Host "✅ 설정 완료!" -ForegroundColor Green
Write-Host "📋 변경된 인코딩 상태:" -ForegroundColor Cyan
Write-Host "  Console Output Encoding: $([Console]::OutputEncoding.EncodingName)" -ForegroundColor White
Write-Host "  PowerShell Output Encoding: $($OutputEncoding.EncodingName)" -ForegroundColor White

# 한글 테스트
Write-Host ""
Write-Host "🧪 한글 표시 테스트:" -ForegroundColor Yellow
Write-Host "  안녕하세요! 한글이 제대로 표시됩니다." -ForegroundColor Green
Write-Host "  🚀 Redis 설정이 완료되었습니다!" -ForegroundColor Green
Write-Host "  📦 Heroku 배포 준비가 되었습니다!" -ForegroundColor Green

Write-Host ""
Write-Host "💡 팁: 이 설정을 영구적으로 적용하려면 PowerShell 프로필에 추가하세요:" -ForegroundColor Cyan
Write-Host "  [Console]::OutputEncoding = [System.Text.Encoding]::UTF8" -ForegroundColor White
Write-Host "  `$OutputEncoding = [System.Text.Encoding]::UTF8" -ForegroundColor White
