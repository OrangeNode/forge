# 从任意工作目录执行统一验收；任一步失败立即退出，保留原工作目录。
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $projectRoot 'backend')
try {
    & .\mvnw.cmd -B clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Backend verification failed.' }
} finally {
    Pop-Location
}

& node (Join-Path $PSScriptRoot 'check-frontend.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Frontend startup and proxy verification failed.' }

Push-Location (Join-Path $projectRoot 'frontend')
try {
    foreach ($check in @('type-check', 'lint', 'test', 'build')) {
        & pnpm run $check
        if ($LASTEXITCODE -ne 0) { throw "Frontend $check failed." }
    }
} finally {
    Pop-Location
}
