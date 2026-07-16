$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot ".." )).Path
$ignored = @(".git", ".gradle", "build", "tmp", "keystore")
$secretPattern = '(?i)(github_pat_[A-Za-z0-9_]{20,}|ghp_[A-Za-z0-9_]{20,}|AIza[0-9A-Za-z_-]{20,}|BEGIN (RSA|OPENSSH|EC|DSA) PRIVATE KEY|(?:password|secret|api[_-]?key|token)\s*[:=]\s*["''][^"'']{8,}["''])'
$badNames = @("local.properties", "keystore.properties")
$badExtensions = @(".jks", ".keystore", ".p12", ".pfx")

$gitFiles = git -C $root ls-files -co --exclude-standard
$files = @($gitFiles | ForEach-Object { Join-Path $root $_ } | ForEach-Object { Get-Item -LiteralPath $_ -Force -ErrorAction SilentlyContinue })

$violations = @()
foreach ($file in $files) {
    if ($badNames -contains $file.Name -or $badExtensions -contains $file.Extension.ToLowerInvariant()) {
        $violations += "forbidden file: $($file.FullName)"
        continue
    }
    if ($file.Length -lt 5MB -and $file.Extension -notin @('.png', '.jpg', '.jpeg', '.gif', '.webp')) {
        $content = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
        if ($content -match $secretPattern) { $violations += "secret-like content: $($file.FullName)" }
    }
}

if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "Privacy check passed: $($files.Count) files checked."
