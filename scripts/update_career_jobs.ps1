param(
    [ValidateRange(1, 10)]
    [int]$LimitPerSource = 3
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$crawler = Join-Path $PSScriptRoot 'career_job_crawler.py'
$target = Join-Path $repoRoot 'src\main\resources\career\job-postings.crawled.json'
$candidate = Join-Path ([System.IO.Path]::GetTempPath()) ('amilingo-career-' + [guid]::NewGuid() + '.json')

try {
    & python $crawler --limit-per-source $LimitPerSource --output $candidate
    if ($LASTEXITCODE -ne 0) {
        throw "Career crawler exited with code $LASTEXITCODE"
    }

    $payload = Get-Content -LiteralPath $candidate -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not $payload.postings -or $payload.postings.Count -lt 1) {
        throw 'Crawler returned no postings; the existing dataset was kept.'
    }

    Copy-Item -LiteralPath $candidate -Destination $target -Force
    Write-Host "Updated $target with $($payload.postings.Count) posting(s)."
    if ($payload.warnings.Count -gt 0) {
        Write-Warning ($payload.warnings -join [Environment]::NewLine)
    }
}
finally {
    if (Test-Path -LiteralPath $candidate) {
        Remove-Item -LiteralPath $candidate -Force
    }
}
