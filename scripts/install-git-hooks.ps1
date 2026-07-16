$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
git -C $root config core.hooksPath .githooks
Write-Host "Git hooks installed for this checkout."
