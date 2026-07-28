param(
    [Parameter(Mandatory)][string]$CsvPath,
    [int]$IdleThresholdPercent = 2
)

$rows = Import-Csv -Path $CsvPath | ForEach-Object {
    [PSCustomObject]@{
        Timestamp = [datetime]::ParseExact($_.timestamp.Trim(), "yyyy/MM/dd HH:mm:ss.fff", $null)
        GpuUtil   = [double]($_.'utilization.gpu [%]' -replace '[^\d.]', '')
        MemUsed   = [double]($_.'memory.used [MiB]' -replace '[^\d.]', '')
    }
}

$baselineMem = ($rows | Select-Object -First 5 | Measure-Object -Property MemUsed -Average).Average

$activeIndices = @()
for ($i = 0; $i -lt $rows.Count; $i++) {
    if ($rows[$i].GpuUtil -gt $IdleThresholdPercent -or ($rows[$i].MemUsed - $baselineMem) -gt 20) {
        $activeIndices += $i
    }
}

if ($activeIndices.Count -eq 0) {
    Write-Host "No se detecto actividad de GPU en el log."
    return
}

$startIdx = [math]::Max(0, ($activeIndices | Select-Object -First 1) - 1)
$endIdx = [math]::Min($rows.Count - 1, ($activeIndices | Select-Object -Last 1) + 1)
$activeRows = $rows[$startIdx..$endIdx]

$totalWeightedUtil = 0.0
$totalDurationMs = 0.0
$peakMem = 0.0

for ($i = 0; $i -lt $activeRows.Count - 1; $i++) {
    $deltaMs = ($activeRows[$i+1].Timestamp - $activeRows[$i].Timestamp).TotalMilliseconds
    $totalWeightedUtil += $activeRows[$i].GpuUtil * $deltaMs
    $totalDurationMs += $deltaMs
    if ($activeRows[$i].MemUsed -gt $peakMem) { $peakMem = $activeRows[$i].MemUsed }
}

$avgGpuPercent = if ($totalDurationMs -gt 0) { $totalWeightedUtil / $totalDurationMs } else { 0 }

Write-Host "Ventana activa: $($activeRows[0].Timestamp) a $($activeRows[-1].Timestamp)"
Write-Host "Duracion activa: $([math]::Round($totalDurationMs, 1)) ms"
Write-Host "Promedio ponderado GPU% (solo actividad): $([math]::Round($avgGpuPercent, 2)) %"
Write-Host "Pico VRAM en ventana activa: $peakMem MiB"