param(
    [Parameter(Mandatory)][string]$ProcessName,
    [Parameter(Mandatory)][string]$Label,
    [int]$IntervalSeconds = 1
)

$outDir = "bench-logs"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$log = Join-Path $outDir "$Label-proc.csv"
"timestamp,cpu_percent,workingset_mb,privatemem_mb" | Out-File -FilePath $log -Encoding utf8

$prevCpuTime = $null
$prevWallTime = $null

Write-Host "Logueando '$ProcessName' cada $IntervalSeconds s. Ctrl+C para cortar."
while ($true) {
    $procs = Get-Process -Name $ProcessName -ErrorAction SilentlyContinue
    if ($procs) {
        $totalCpuTime = [TimeSpan]::FromTicks((($procs | ForEach-Object { $_.TotalProcessorTime.Ticks }) | Measure-Object -Sum).Sum)
        $nowWall = Get-Date

        $cpuPercent = 0
        if ($prevCpuTime -ne $null) {
            $deltaCpuMs = ($totalCpuTime - $prevCpuTime).TotalMilliseconds
            $deltaWallMs = ($nowWall - $prevWallTime).TotalMilliseconds
            if ($deltaWallMs -gt 0) {
                $cpuPercent = [math]::Round(($deltaCpuMs / $deltaWallMs) * 100, 1)
            }
        }

        $prevCpuTime = $totalCpuTime
        $prevWallTime = $nowWall

        $ws = [math]::Round(($procs | Measure-Object WorkingSet64 -Sum).Sum / 1MB, 1)
        $pm = [math]::Round(($procs | Measure-Object PrivateMemorySize64 -Sum).Sum / 1MB, 1)
        "$(Get-Date -Format o),$cpuPercent,$ws,$pm" | Out-File -FilePath $log -Append -Encoding utf8
    }
    Start-Sleep -Seconds $IntervalSeconds
}