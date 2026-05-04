# 로컬 개발: .env.local 자동 로드 + Tesseract 경로 + Spring Boot 실행.
# 사용: PowerShell 에서 ./run-local.ps1
# 사전 조건:
#   - .env.local 파일에 시크릿(OPEN_AI_KEY, DB_PASSWORD 등) 작성
#   - Tesseract 설치 (C:\Program Files\Tesseract-OCR)
$ErrorActionPreference = "Stop"

# --- .env.local 로드 (있으면) ---
$envFile = Join-Path $PSScriptRoot ".env.local"
if (Test-Path $envFile) {
    Write-Host "Loading $envFile" -ForegroundColor Cyan
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $idx = $line.IndexOf("=")
        if ($idx -le 0) { return }
        $name = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        # 양쪽 따옴표 제거 (있으면)
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        Set-Item -Path "env:$name" -Value $value
    }
} else {
    Write-Warning ".env.local not found at $envFile — secrets must be set elsewhere"
}

# --- Tesseract 경로 자동 추가 ---
$tess = "C:\Program Files\Tesseract-OCR"
if (Test-Path "$tess\tesseract.exe") {
    if (-not ($env:Path -split ";" | Where-Object { $_ -eq $tess })) {
        $env:Path = "$tess;$env:Path"
    }
    if (-not $env:TESSDATA_PREFIX) {
        $env:TESSDATA_PREFIX = "$tess\tessdata"
    }
    Write-Host "Tesseract found at $tess" -ForegroundColor Green
} else {
    Write-Warning "Tesseract not found at $tess — OCR may fail. Install from https://github.com/UB-Mannheim/tesseract/wiki"
}

# --- 필수 값 검증 ---
if (-not $env:OPEN_AI_KEY) {
    Write-Error "OPEN_AI_KEY missing. Set it in .env.local"
    exit 1
}
if (-not $env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE = "local" }

Write-Host "Profile: $env:SPRING_PROFILES_ACTIVE | OCR: $env:PDF_OCR_STRATEGY | DB: $env:DB_URL" -ForegroundColor Cyan

# --- 실행 ---
& ./gradlew bootRun
