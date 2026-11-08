#Requires -Version 7
<#
.SYNOPSIS
  Installs the OTA/firmware unpacking tools user-locally (no admin). Idempotent; SHA256-verified
  against the digests GitHub publishes for each release asset. Owner-approved 2026-11-07.
    payload-dumper-go 2.1.0  (ssut, Apache-2.0)        payload.bin -> partition images
    erofs-utils 1.8.10       (sekaiacg Windows build, GPL-2.0)  read/extract EROFS images
    7-Zip 26.05              (ip7z, LGPL)                       read ext4 images (vendor_dlkm)
#>
param([string]$Root = (Join-Path $env:LOCALAPPDATA 'Programs'))
$ErrorActionPreference = 'Stop'

function Get-Asset([string]$Repo, [string]$Tag, [string]$Name) {
    $dir = Join-Path ([IO.Path]::GetTempPath()) "stylo-dl\$($Repo -replace '/', '_')"
    New-Item -ItemType Directory -Force $dir | Out-Null
    $a = (Invoke-RestMethod "https://api.github.com/repos/$Repo/releases/tags/$Tag").assets | Where-Object name -eq $Name
    $out = Join-Path $dir $Name
    if (-not (Test-Path $out)) { curl.exe -L --fail -sS -o $out $a.browser_download_url }
    if ((Get-FileHash $out -Algorithm SHA256).Hash.ToLower() -ne ($a.digest -replace '^sha256:', '')) { Remove-Item $out; throw "$Name checksum mismatch" }
    Write-Host "  $Name  SHA256 OK"; $out
}

$pd = Join-Path $Root 'payload-dumper-go-2.1.0'
if (-not (Test-Path "$pd\payload-dumper-go.exe")) {
    New-Item -ItemType Directory -Force $pd | Out-Null
    tar -xzf (Get-Asset 'ssut/payload-dumper-go' '2.1.0' 'payload-dumper-go_2.1.0_windows_amd64_avx2.tar.gz') -C $pd
}
$eu = Join-Path $Root 'erofs-utils-1.8.10'
if (-not (Test-Path "$eu\extract.erofs.exe")) {
    Expand-Archive (Get-Asset 'sekaiacg/erofs-utils' 'v1.8.10-251217' 'erofs-utils-v1.8.10-gee46dd74-251217-Cygwin_x86_64.zip') $eu -Force
}
$sz = Join-Path $Root '7zip-26.05'   # reads ext4 images (vendor_dlkm); 26.05, not the same-day 26.06
if (-not (Test-Path "$sz\7z.exe")) {
    $zr = Get-Asset 'ip7z/7zip' '26.05' '7zr.exe'
    & $zr x (Get-Asset 'ip7z/7zip' '26.05' '7z2605-x64.exe') "-o$sz" -y | Out-Null
}
Write-Host "7-Zip:             $sz\7z.exe"
Write-Host "payload-dumper-go: $pd\payload-dumper-go.exe"
Write-Host "extract.erofs:     $eu\extract.erofs.exe"
