#Requires -Version 7
<#
.SYNOPSIS
  Installs OpenCode V2 user-locally (no admin). Idempotent.
.DESCRIPTION
  Downloads the official Windows x64 zip from GitHub releases (anomalyco/opencode), verifies it
  against the release SHA256SUMS, installs to %LOCALAPPDATA%\Programs\OpenCode and adds that
  folder to the *user* PATH. If the requested version is already installed, it does nothing.
  The V2 docs list no Windows package managers, so none is used.
.EXAMPLE
  ./Install-OpenCode.ps1 -Version v2.2.6
#>
param(
    [string]$Version = 'v2.2.6',
    [string]$Dest = (Join-Path $env:LOCALAPPDATA 'Programs\OpenCode')
)
$ErrorActionPreference = 'Stop'
$exe = Join-Path $Dest 'opencode.exe'

if (Test-Path $exe) {
    $current = (& $exe --version 2>$null) -replace '^opencode\s+', ''
    if ($current -eq $Version) { Write-Host "OpenCode $Version already installed at $exe"; return }
    Write-Host "Found OpenCode $current, installing $Version"
}

$tmp = Join-Path ([IO.Path]::GetTempPath()) "opencode-$Version"
New-Item -ItemType Directory -Force $tmp | Out-Null
$base = "https://github.com/anomalyco/opencode/releases/download/$Version"
$zip = Join-Path $tmp 'opencode-windows-x64.zip'
Invoke-WebRequest "$base/opencode-windows-x64.zip" -OutFile $zip
Invoke-WebRequest "$base/SHA256SUMS" -OutFile (Join-Path $tmp 'SHA256SUMS')

$line = Select-String -Path (Join-Path $tmp 'SHA256SUMS') -Pattern 'opencode-windows-x64\.zip$'
if (-not $line) { throw 'opencode-windows-x64.zip not listed in SHA256SUMS' }
$expected = $line.Line.Split(' ')[0].ToLower()
$actual = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
if ($expected -ne $actual) { throw "Checksum mismatch: expected $expected, got $actual" }

Expand-Archive $zip -DestinationPath (Join-Path $tmp 'x') -Force
New-Item -ItemType Directory -Force $Dest | Out-Null
Copy-Item (Join-Path $tmp 'x\opencode.exe') $exe -Force

$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
if (($userPath -split ';') -notcontains $Dest) {
    [Environment]::SetEnvironmentVariable('Path', ($userPath.TrimEnd(';') + ";$Dest"), 'User')
    Write-Host "Added $Dest to user PATH (open a new terminal to pick it up)"
}
& $exe --version
