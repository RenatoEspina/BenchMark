llamacpp-server-monitor.ps1param(
    [Parameter(Mandatory)][string]$Label,
    [Parameter(Mandatory)][string[]]$JavaArgs,
    [string]$ServerProcessName = "llama-server"
)

$outDir = "bench-logs"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$serverProc = Get-Process -Name $ServerProcessName -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $serverProc) {
    Write-Host "No se encontro proceso '$ServerProcessName'. Inicia el servidor primero."
    return
}

$monitorJob = Start-Job -ScriptBlock {
    param($procId, $intervalMs)
    $samples = @()
    $prevCpu = $null
    $prevWall = $null
    while ($true) {
        try {
            $p = Get-Process -Id $procId -ErrorAction Stop
        } catch {
            break
        }
        $nowWall = Get-Date
        if ($prevCpu -ne $null) {
            $deltaCpuMs = ($p.TotalProcessorTime - $prevCpu).TotalMilliseconds
            $deltaWallMs = ($nowWall - $prevWall).TotalMilliseconds
            $cpuPercent = if ($deltaWallMs -gt 0) { [math]::Round(($deltaCpuMs / $deltaWallMs) * 100, 1) } else { 0 }
            $ws = [math]::Round($p.WorkingSet64 / 1MB, 1)
            $samples += [PSCustomObject]@{ CpuPercent = $cpuPercent; WorkingSetMb = $ws }
        }
        $prevCpu = $p.TotalProcessorTime
        $prevWall = $nowWall
        Start-Sleep -Milliseconds $intervalMs
    }
    return $samples
} -ArgumentList $serverProc.Id, 100

Write-Host "Ejecutando: java $($JavaArgs -join ' ')"
$sw = [System.Diagnostics.Stopwatch]::StartNew()
& java @JavaArgs
$sw.Stop()

Stop-Job $monitorJob | Out-Null
$samples = Receive-Job $monitorJob
Remove-Job $monitorJob -Force | Out-Null

if ($samples -and $samples.Count -gt 0) {
    $avgCpu = [math]::Round(($samples | Measure-Object -Property CpuPercent -Average).Average, 1)
    $peakCpu = ($samples | Measure-Object -Property CpuPercent -Maximum).Maximum
    $peakWs = ($samples | Measure-Object -Property WorkingSetMb -Maximum).Maximum

    Write-Host ""
    Write-Host "=== Resumen llama-server (durante esta request) ==="
    Write-Host "Duracion wall (cliente): $([math]::Round($sw.Elapsed.TotalMilliseconds,1)) ms"
    Write-Host "CPU% promedio: $avgCpu %"
    Write-Host "CPU% pico: $peakCpu %"
    Write-Host "Pico RAM (workingset): $peakWs MB"

    $log = Join-Path $outDir "$Label-server-proc.csv"
    $samples | Export-Csv -Path $log -NoTypeInformation
    Write-Host "Log completo en: $log"
} else {
    Write-Host "No se pudieron tomar muestras del proceso llama-server."
}