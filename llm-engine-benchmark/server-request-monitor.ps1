param(
    [Parameter(Mandatory)][string]$Label,
    [Parameter(Mandatory)][string[]]$JavaArgs,
    [string]$ServerProcessName = "llama-server",
    [int]$IntervalMs = 50
)

$outDir = "bench-logs"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$serverProc = Get-Process -Name $ServerProcessName -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $serverProc) {
    Write-Host "No se encontro proceso '$ServerProcessName'. Inicia el servidor primero."
    return
}

$samples = New-Object System.Collections.Generic.List[object]
$prevCpu = $null
$prevWall = $null

Write-Host "Ejecutando: java $($JavaArgs -join ' ')"
$javaProc = Start-Process java -ArgumentList $JavaArgs -PassThru -NoNewWindow
$sw = [System.Diagnostics.Stopwatch]::StartNew()

while (-not $javaProc.HasExited) {
    try {
        $serverProc.Refresh()
    } catch {
        break
    }
    $nowWall = Get-Date
    if ($prevCpu -ne $null) {
        $deltaCpuMs = ($serverProc.TotalProcessorTime - $prevCpu).TotalMilliseconds
        $deltaWallMs = ($nowWall - $prevWall).TotalMilliseconds
        $cpuPercent = if ($deltaWallMs -gt 0) { [math]::Round(($deltaCpuMs / $deltaWallMs) * 100, 1) } else { 0 }
        $ws = [math]::Round($serverProc.WorkingSet64 / 1MB, 1)
        $samples.Add([PSCustomObject]@{ CpuPercent = $cpuPercent; WorkingSetMb = $ws })
    }
    $prevCpu = $serverProc.TotalProcessorTime
    $prevWall = $nowWall
    Start-Sleep -Milliseconds $IntervalMs
}
$sw.Stop()
$javaProc.WaitForExit()

if ($samples.Count -gt 0) {
    $avgCpu = [math]::Round(($samples | Measure-Object -Property CpuPercent -Average).Average, 1)
    $peakCpu = ($samples | Measure-Object -Property CpuPercent -Maximum).Maximum
    $peakWs = ($samples | Measure-Object -Property WorkingSetMb -Maximum).Maximum

    Write-Host ""
    Write-Host "=== Resumen $ServerProcessName (durante esta request) ==="
    Write-Host "Duracion wall (cliente): $([math]::Round($sw.Elapsed.TotalMilliseconds,1)) ms"
    Write-Host "CPU% promedio: $avgCpu %"
    Write-Host "CPU% pico: $peakCpu %"
    Write-Host "Pico RAM (workingset): $peakWs MB"

    $log = Join-Path $outDir "$Label-server-proc.csv"
    $samples | Export-Csv -Path $log -NoTypeInformation
    Write-Host "Log completo en: $log"
} else {
    Write-Host "No se pudieron tomar muestras del proceso $ServerProcessName (la request fue mas rapida que el intervalo de muestreo)."
}