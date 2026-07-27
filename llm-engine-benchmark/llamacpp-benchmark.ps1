param(
    [Parameter(Mandatory)][string]$Label,
    [Parameter(Mandatory)][string]$Binary,
    [Parameter(Mandatory)][string]$ModelPath,
    [int]$NGpuLayers = 0,
    [int]$CtxSize = 4096,
    [switch]$LogGpu
)

$outDir = "bench-logs"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$javaArgs = @(
    "-Dfile.encoding=UTF-8",
    "-Dllamacpp.binary=$Binary",
    "-Dllamacpp.ctxSize=$CtxSize"
)
if ($NGpuLayers -gt 0) {
    $javaArgs += "-Dllamacpp.nGpuLayers=$NGpuLayers"
}
$javaArgs += @("-jar", "benchmark-app\target\benchmark-app.jar", "--engine=LLAMA_CPP", "--model=$ModelPath")

$gpuJob = $null
$gpuLog = Join-Path $outDir "$Label-gpu.csv"
if ($LogGpu) {
    $gpuJob = Start-Job -ScriptBlock {
        param($path)
        cmd /c "nvidia-smi --query-gpu=timestamp,utilization.gpu,utilization.memory,memory.used,memory.total --format=csv -lms 200" | Out-File -FilePath $path -Encoding utf8
    } -ArgumentList $gpuLog
}

Write-Host "Lanzando benchmark-app..."
$appProc = Start-Process java -ArgumentList $javaArgs -PassThru -NoNewWindow

$llamaCliProc = $null
$peakWorkingSetMb = 0
$pollStart = Get-Date
while (-not $llamaCliProc -and ((Get-Date) - $pollStart).TotalSeconds -lt 30) {
    $llamaCliProc = Get-Process -Name "llama-cli" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $llamaCliProc) { Start-Sleep -Milliseconds 20 }
}

if ($llamaCliProc) {
    Write-Host "llama-cli detectado (PID $($llamaCliProc.Id)), midiendo..."
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while (-not $llamaCliProc.HasExited) {
        try {
            $llamaCliProc.Refresh()
            $ws = [math]::Round($llamaCliProc.WorkingSet64 / 1MB, 1)
            if ($ws -gt $peakWorkingSetMb) { $peakWorkingSetMb = $ws }
        } catch {}
        Start-Sleep -Milliseconds 20
    }
    $sw.Stop()
    $llamaCliProc.Refresh()
    $cpuTimeMs = $llamaCliProc.TotalProcessorTime.TotalMilliseconds
    $wallMs = $sw.Elapsed.TotalMilliseconds
    $cpuPercent = if ($wallMs -gt 0) { [math]::Round(($cpuTimeMs / $wallMs) * 100, 1) } else { 0 }

    Write-Host ""
    Write-Host "=== Resumen llama-cli ==="
    Write-Host "Duracion (wall): $([math]::Round($wallMs,1)) ms"
    Write-Host "CPU acumulada: $([math]::Round($cpuTimeMs,1)) ms"
    Write-Host "CPU%: $cpuPercent %"
    Write-Host "Pico RAM (workingset): $peakWorkingSetMb MB"
} else {
    Write-Host "No se detecto proceso llama-cli en 30s."
}

$appProc.WaitForExit()

if ($gpuJob) {
    Stop-Job $gpuJob | Out-Null
    Remove-Job $gpuJob -Force | Out-Null
    Write-Host "Log GPU: $gpuLog"
}
