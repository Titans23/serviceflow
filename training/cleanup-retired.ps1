param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskManifest = Join-Path $taskRoot 'runtime-data/maintenance/cleanup-2026-09-17/artifact-deletions.json'
if (-not (Test-Path -LiteralPath $taskManifest)) { throw 'Local retirement manifest not found. This one-time cleanup is not required on a fresh clone.' }
$taskItems = Get-Content -LiteralPath $taskManifest -Raw | ConvertFrom-Json
# This is an explicitly enumerated, local retirement list. No wildcard deletion.
$taskAllowedRoots = @('training', 'runtime-data/training', 'local-datasets/serviceflow') |
    ForEach-Object { [IO.Path]::GetFullPath((Join-Path $taskRoot $_)) + [IO.Path]::DirectorySeparatorChar }
$taskProtected = @(
    'runtime-data/training/models',
    'runtime-data/training/grader-specialist-exp-01/merged-seed-42-epoch-2',
    'runtime-data/training/grader-specialist-exp-01/seed-42/checkpoints',
    'runtime-data/training/grader-specialist-exp-01/seed-42/epoch-adapters/epoch-2',
    'runtime-data/training/grader-specialist-exp-01/resume-engine',
    'local-datasets/serviceflow/grader-specialist-exp-01',
    'local-datasets/serviceflow/raw/grader-specialist-exp-01'
) | ForEach-Object { [IO.Path]::GetFullPath((Join-Path $taskRoot $_)) }
$taskVerified = foreach ($taskItem in $taskItems) {
    if (-not (Test-Path -LiteralPath $taskItem.path)) { continue }
    $taskPath = (Resolve-Path -LiteralPath $taskItem.path).Path
    if (-not ($taskAllowedRoots | Where-Object { $taskPath.StartsWith($_, [StringComparison]::OrdinalIgnoreCase) })) { throw "Outside allowed roots: $taskPath" }
    foreach ($taskKeep in $taskProtected) {
        if ($taskPath -eq $taskKeep -or $taskKeep.StartsWith($taskPath + '\', [StringComparison]::OrdinalIgnoreCase) -or $taskPath.StartsWith($taskKeep + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "Protected artifact: $taskPath" }
    }
    $taskEntry = Get-Item -LiteralPath $taskPath -Force
    if ($taskEntry.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Linked target: $taskPath" }
    if ($taskEntry.PSIsContainer) {
        $taskLinks = Get-ChildItem -LiteralPath $taskPath -Recurse -Force | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }
        if ($taskLinks) { throw "Linked subtree requires review: $taskPath" }
    }
    [pscustomobject]@{ Path = $taskPath; Bytes = $taskItem.bytes }
}
$taskVerified | Select-Object Path, Bytes | Format-Table -AutoSize
if (-not $Apply) { Write-Output 'Preview only. Pass -Apply to delete exactly these retired artifacts.'; exit 0 }
# Stop if the old local service ports have been reused or restarted.
if (Get-NetTCPConnection -State Listen -LocalPort 18000,18080,15173 -ErrorAction SilentlyContinue) { throw 'Stop local model/backend/frontend services before deletion.' }
foreach ($taskItem in $taskVerified) { Remove-Item -LiteralPath $taskItem.Path -Recurse -Force }
@{ deleted = $taskVerified.Count; completedAt = [DateTime]::UtcNow.ToString('o') } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRoot 'runtime-data/maintenance/cleanup-2026-09-17/deletion-completed.json')
