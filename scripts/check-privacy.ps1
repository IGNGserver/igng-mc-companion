$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot ".." )).Path
$secretPattern = '(?i)(github_pat_[A-Za-z0-9_]{20,}|ghp_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{20,}|xox[baprs]-[A-Za-z0-9-]{20,}|BEGIN (RSA|OPENSSH|EC|DSA) PRIVATE KEY|(?:password|passwd|secret|api[_-]?key|access[_-]?token|private[_-]?key)\s*[:=]\s*["''][^"'']{8,}["''])'
$badNames = @("local.properties", "keystore.properties")
$badExtensions = @(".jks", ".keystore", ".p12", ".pfx")

$stagedFiles = @(git -C $root diff --cached --name-only --diff-filter=ACMRT)
$scanIndex = $stagedFiles.Count -gt 0
$relativeFiles = if ($scanIndex) { $stagedFiles } else { @(git -C $root ls-files -co --exclude-standard) }
$binaryExtensions = @('.png', '.jpg', '.jpeg', '.gif', '.webp', '.ico', '.apk', '.jar', '.class', '.bin', '.jks', '.keystore')

$violations = @()
foreach ($relativePath in $relativeFiles) {
    $file = Get-Item -LiteralPath (Join-Path $root $relativePath) -Force -ErrorAction SilentlyContinue
    $fileName = Split-Path -Leaf $relativePath
    $extension = [IO.Path]::GetExtension($relativePath).ToLowerInvariant()
    if ($badNames -contains $fileName -or $badExtensions -contains $extension) {
        $violations += "forbidden file: $relativePath"
        continue
    }
    if ($extension -notin $binaryExtensions) {
        if ($scanIndex) {
            $content = git -C $root show --no-ext-diff --textconv ":$relativePath" 2>$null | Out-String
        } elseif ($file -and $file.Length -lt 5MB) {
            $content = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
        } else {
            $content = ''
        }
        if ($content -match $secretPattern) { $violations += "secret-like content: $relativePath" }
    }
}

if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Error $_ }
    exit 1
}

$scope = if ($scanIndex) { 'staged files' } else { 'working tree files' }
Write-Host "Privacy check passed: $($relativeFiles.Count) $scope checked."
