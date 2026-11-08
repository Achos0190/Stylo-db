<#
  run_local.ps1 - Offload a task to the local model (Qwen via LM Studio) through OpenCode.

  You run this; it launches an auto-approving local OpenCode agent on your machine.
  Claude prepares the task file; you invoke this so a human authorizes the autonomous run.

  Usage:
    .\workstation\scripts\run_local.ps1 -TaskFile <path-to-task.txt> [-Model qwen3.8-27b] [-ReadOnly]

  -ReadOnly  Appends a hard reminder that the agent must not modify tracked files.
  Logs go to research\local\opencode-logs\ (git-ignored). The log path is printed at the end.
#>
param(
  [Parameter(Mandatory = $true)] [string] $TaskFile,
  [string] $Model = "qwen3.8-27b",
  [switch] $ReadOnly
)

$ErrorActionPreference = "Stop"
$OC = "C:\Users\Vincent\AppData\Local\Programs\OpenCode\opencode.exe"
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent  # ...\Stylo

if (-not (Test-Path $OC))       { throw "OpenCode not found at $OC" }
if (-not (Test-Path $TaskFile)) { throw "Task file not found: $TaskFile" }

# LM Studio must be up with the model loaded.
try {
  $ids = (Invoke-RestMethod "http://127.0.0.1:1234/v1/models" -TimeoutSec 5).data.id
} catch {
  throw "LM Studio API not responding on :1234. Start LM Studio and load '$Model'."
}
if ($ids -notcontains $Model) {
  throw "Model '$Model' is not loaded in LM Studio. Loaded: $($ids -join ', ')"
}

$task = Get-Content $TaskFile -Raw
if ($ReadOnly) {
  $task += "`n`nHARD RULE: This is read-only analysis. Do not create, edit, move, or delete any file outside the system temp directory. Never touch anything under research/ except to read it."
}

$logDir = Join-Path $repo "research\local\opencode-logs"
New-Item -ItemType Directory -Force $logDir | Out-Null
$stamp  = Get-Date -Format "yyyyMMdd-HHmmss"
$log    = Join-Path $logDir "run-$stamp.log"

Write-Host "Model : lmstudio/$Model"
Write-Host "Task  : $TaskFile"
Write-Host "Log   : $log"
Write-Host "Running (Qwen is ~26 tok/s; a multi-step task can take a few minutes)..."
Write-Host ("-" * 60)

Push-Location $repo
try {
  "[started $(Get-Date -Format o)] model=$Model task=$TaskFile" | Set-Content $log
  & $OC run --auto --archived --model "lmstudio/$Model" $task 2>&1 | Tee-Object -FilePath $log -Append
  "[exit $LASTEXITCODE at $(Get-Date -Format o)]" | Add-Content $log
} finally {
  Pop-Location
}

Write-Host ("-" * 60)
Write-Host "Done. Full transcript: $log"
