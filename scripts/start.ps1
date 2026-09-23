$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$jar = Join-Path $repo 'target\parking-data-collector-0.1.0-SNAPSHOT.jar'
$data = Join-Path $repo 'data'
$pidFile = Join-Path $data 'collector.pid'

if (-not (Test-Path -LiteralPath $jar)) {
    throw 'Build the project first: mvn package'
}

if (Test-Path -LiteralPath $pidFile) {
    $existingId = [int](Get-Content -LiteralPath $pidFile -Raw)
    $existing = Get-CimInstance Win32_Process -Filter "ProcessId=$existingId" -ErrorAction SilentlyContinue
    if ($existing -and $existing.CommandLine -like "*$jar*") {
        Write-Output "Collector already running (PID $existingId)"
        return
    }
}

New-Item -ItemType Directory -Path $data -Force | Out-Null
$java = (Get-Command java.exe).Source
$process = Start-Process -FilePath $java -ArgumentList @('-jar', $jar) -WorkingDirectory $repo `
    -WindowStyle Hidden -RedirectStandardOutput (Join-Path $data 'collector.stdout.log') `
    -RedirectStandardError (Join-Path $data 'collector.stderr.log') -PassThru
Set-Content -LiteralPath $pidFile -Value $process.Id
Write-Output "Collector started (PID $($process.Id))"
