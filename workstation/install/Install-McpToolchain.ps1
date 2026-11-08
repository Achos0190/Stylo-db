#Requires -Version 7
<#
.SYNOPSIS
  Installs the Stylo MCP toolchain user-locally (no admin). Idempotent; every download is
  SHA256-verified against the digest GitHub publishes for the release asset.
.DESCRIPTION
  Installs into %LOCALAPPDATA%\Programs:
    jadx-1.5.6                 JADX CLI + GUI (uses the system JDK 21)
    jadx-ai-mcp 6.5.0 plugin   installed into JADX's plugin store
    jadx-mcp-server-6.5.0      Python MCP server (own venv)
    android-mcp                @us-all/android-mcp 1.14.4 (pinned npm install, not npx @latest)
    ghidra_12.1.4_PUBLIC       Ghidra (the version GhidraMCP 2.0 is built for, not the latest 12.2)
    ghidra-mcp-2.0             LaurieWired GhidraMCP 2.0: extension jar + headless server (own venv)
  Prerequisites: JDK 21, Python 3.13 (py launcher), Node >= 22, adb.
  Frida is deliberately not installed (see workstation/README.md).
#>
param([string]$Root = (Join-Path $env:LOCALAPPDATA 'Programs'))
$ErrorActionPreference = 'Stop'
$dl = Join-Path ([IO.Path]::GetTempPath()) 'stylo-dl'

function Get-Asset([string]$Repo, [string]$Tag, [string]$Name) {
    $dir = Join-Path $dl ($Repo -replace '/', '_'); New-Item -ItemType Directory -Force $dir | Out-Null
    $rel = Invoke-RestMethod "https://api.github.com/repos/$Repo/releases/tags/$Tag"
    $a = $rel.assets | Where-Object name -eq $Name
    if (-not $a) { throw "$Name not found in $Repo $Tag" }
    $out = Join-Path $dir $Name
    if (-not (Test-Path $out)) { curl.exe -L --fail -C - --retry 10 --retry-all-errors -sS -o $out $a.browser_download_url }
    $exp = $a.digest -replace '^sha256:', ''
    $act = (Get-FileHash $out -Algorithm SHA256).Hash.ToLower()
    if (-not $exp) { throw "$Name has no published digest; verify manually (sha256 $act)" }
    if ($exp -ne $act) { Remove-Item $out; throw "$Name checksum mismatch" }
    Write-Host "  $Name  SHA256 OK"
    $out
}
function New-Venv([string]$Dir) { if (-not (Test-Path "$Dir\.venv")) { py -3.13 -m venv "$Dir\.venv" }; "$Dir\.venv\Scripts\python.exe" }

Write-Host '[1/5] JADX 1.5.6'
$jadxDir = Join-Path $Root 'jadx-1.5.6'
if (-not (Test-Path "$jadxDir\bin\jadx.bat")) { Expand-Archive (Get-Asset 'skylot/jadx' 'v1.5.6' 'jadx-1.5.6.zip') $jadxDir -Force }
$jadx = "$jadxDir\bin\jadx.bat"

Write-Host '[2/5] jadx-ai-mcp 6.5.0 (plugin + server)'
if (-not ((& $jadx plugins --list 2>&1) -match 'jadx-ai-mcp')) {
    & $jadx plugins --install-jar (Get-Asset 'zinja-coder/jadx-ai-mcp' 'v6.5.0' 'jadx-ai-mcp-6.5.0.jar') | Out-Null
}
$jms = Join-Path $Root 'jadx-mcp-server-6.5.0'
if (-not (Test-Path "$jms\jadx_mcp_server.py")) {
    $tmp = Join-Path $dl 'jadx-mcp-server-x'
    Expand-Archive (Get-Asset 'zinja-coder/jadx-ai-mcp' 'v6.5.0' 'jadx-mcp-server-6.5.0.zip') $tmp -Force
    Copy-Item (Join-Path $tmp 'jadx-mcp-server') $jms -Recurse
}
& (New-Venv $jms) -m pip install -q --disable-pip-version-check 'fastmcp>=3.0.2' httpx

Write-Host '[3/5] @us-all/android-mcp 1.14.4'
$am = Join-Path $Root 'android-mcp'
if (-not (Test-Path "$am\node_modules\@us-all\android-mcp\package.json")) {
    New-Item -ItemType Directory -Force $am | Out-Null
    npm install --prefix $am --no-fund --no-audit '@us-all/android-mcp@1.14.4' | Out-Null
}

Write-Host '[4/5] Ghidra 12.1.4'
if (-not (Test-Path "$Root\ghidra_12.1.4_PUBLIC\ghidraRun.bat")) {
    Expand-Archive (Get-Asset 'NationalSecurityAgency/ghidra' 'Ghidra_12.1.4_build' 'ghidra_12.1.4_PUBLIC_20260921.zip') $Root -Force
}

Write-Host '[5/5] GhidraMCP 2.0 (headless)'
$gm = Join-Path $Root 'ghidra-mcp-2.0'
if (-not ((Test-Path "$gm\extension\GhidraMCP\lib\GhidraMCP.jar") -and (Test-Path "$gm\.venv\Scripts\ghidra-mcp.exe"))) {
    $tmp = Join-Path $dl 'ghidramcp-x'
    Expand-Archive (Get-Asset 'LaurieWired/GhidraMCP' '2.0' 'GhidraMCP-release-2-0.zip') $tmp -Force
    $rel = Join-Path $tmp 'GhidraMCP-release-2-0'
    Expand-Archive (Join-Path $rel 'ghidra_12.1.4_PUBLIC_20261010_GhidraMCP.zip') "$gm\extension" -Force
    $whl = (Get-Item (Join-Path $rel 'ghidra_mcp-2.0.0-py3-none-any.whl')).FullName -replace '\\', '/'
    & (New-Venv $gm) -m pip install -q --disable-pip-version-check "ghidra-mcp[headless] @ file:///$whl"
}

Write-Host 'Done. Validate with workstation/scripts/mcp_smoke.py (see workstation/README.md).'
