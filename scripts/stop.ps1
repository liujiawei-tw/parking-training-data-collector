$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$pidFile = Join-Path $repo 'data\collector.pid'
$jar = Join-Path $repo 'target\parking-data-collector-0.1.0-SNAPSHOT.jar'

if (-not (Test-Path -LiteralPath $pidFile)) {
    Write-Output 'No collector PID file found'
    return
}

$collectorId = [int](Get-Content -LiteralPath $pidFile -Raw)
$process = Get-CimInstance Win32_Process -Filter "ProcessId=$collectorId" -ErrorAction SilentlyContinue
if (-not $process) {
    Remove-Item -LiteralPath $pidFile
    Write-Output 'Collector was not running; removed stale PID file'
    return
}
if ($process.CommandLine -notlike "*$jar*") {
    throw "PID $collectorId does not belong to this collector; nothing stopped"
}
Stop-Process -Id $collectorId -ErrorAction Stop
Remove-Item -LiteralPath $pidFile
Write-Output "Collector stopped (PID $collectorId)"
