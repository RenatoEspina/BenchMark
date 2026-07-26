param(
    [Parameter(Mandatory)][string]$Label,
    [Parameter(Mandatory)][string[]]$JavaArgs,
    [int]$IntervalSeconds = 1
)

$outDir = "bench-logs"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$gpuLog = Join-Path $outDir "$Label-gpu.csv"

$job = Start-Job -ScriptBlock {
    param($path, $interval)
    cmd /c "nvidia-smi --query-gpu=timestamp,utilization.gpu,utilization.memory,memory.used,memory.total --format=csv -l $interval" | Out-File -FilePath $path -Encoding utf8
} -ArgumentList $gpuLog, $IntervalSeconds

Write-Host "Corriendo: java $($JavaArgs -join ' ')"
$sw = [System.Diagnostics.Stopwatch]::StartNew()
& java @JavaArgs
$sw.Stop()

Stop-Job $job | Out-Null
Remove-Job $job -Force | Out-Null

Write-Host "Duracion total: $($sw.Elapsed)"

if (Test-Path $gpuLog) {
    $rows = Get-Content $gpuLog | Select-Object -Skip 1
    $usedMb = $rows | ForEach-Object {
        $parts = $_ -split ','
        if ($parts.Count -ge 4) { [int]($parts[3] -replace '[^\d]', '') }
    }
    if ($usedMb) {
        Write-Host "Pico VRAM: $($usedMb | Measure-Object -Maximum | Select-Object -ExpandProperty Maximum) MiB"
        Write-Host "Promedio VRAM: $([math]::Round(($usedMb | Measure-Object -Average).Average, 1)) MiB"
    }
}

Write-Host "Log completo en: $gpuLog"
