param(
    [ValidateRange(1, 10)]
    [int]$LimitPerSource = 3,

    [ValidateSet('bytedance', 'wynn-macau', 'hkjc', 'mtr', 'sands-china', 'baidu-internship', 'baidu-campus')]
    [string[]]$Sources = @()
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$crawler = Join-Path $PSScriptRoot 'career_job_crawler.py'
$auditor = Join-Path $PSScriptRoot 'career_job_audit.py'
$target = Join-Path $repoRoot 'src\main\resources\career\job-postings.crawled.json'
$candidate = Join-Path ([System.IO.Path]::GetTempPath()) ('amilingo-career-' + [guid]::NewGuid() + '.json')
$auditedCandidate = Join-Path ([System.IO.Path]::GetTempPath()) ('amilingo-career-audited-' + [guid]::NewGuid() + '.json')
$auditReport = Join-Path ([System.IO.Path]::GetTempPath()) ('amilingo-career-audit-' + [guid]::NewGuid() + '.json')

try {
    $crawlerArguments = @($crawler, '--limit-per-source', $LimitPerSource, '--output', $candidate)
    foreach ($source in $Sources) {
        $crawlerArguments += @('--source', $source)
    }
    & python @crawlerArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Career crawler exited with code $LASTEXITCODE"
    }

    $payload = Get-Content -LiteralPath $candidate -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not $payload.postings -or $payload.postings.Count -lt 1) {
        throw 'Crawler returned no postings; the existing dataset was kept.'
    }

    & python $auditor --input $candidate --output $auditedCandidate --report $auditReport
    if ($LASTEXITCODE -ne 0) {
        $details = if (Test-Path -LiteralPath $auditReport) { Get-Content -LiteralPath $auditReport -Raw -Encoding UTF8 } else { 'No audit report was produced.' }
        throw "Career dataset audit failed; the existing dataset was kept.`n$details"
    }

    $auditedPayload = Get-Content -LiteralPath $auditedCandidate -Raw -Encoding UTF8 | ConvertFrom-Json
    Copy-Item -LiteralPath $auditedCandidate -Destination $target -Force
    Write-Host "Updated $target with $($auditedPayload.postings.Count) audited posting(s)."
    if ($payload.warnings.Count -gt 0) {
        Write-Warning ($payload.warnings -join [Environment]::NewLine)
    }
}
finally {
    if (Test-Path -LiteralPath $candidate) {
        Remove-Item -LiteralPath $candidate -Force
    }
    if (Test-Path -LiteralPath $auditedCandidate) {
        Remove-Item -LiteralPath $auditedCandidate -Force
    }
    if (Test-Path -LiteralPath $auditReport) {
        Remove-Item -LiteralPath $auditReport -Force
    }
}
