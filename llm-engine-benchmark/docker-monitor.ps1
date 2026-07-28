# docker-monitor.ps1
param(
    [Parameter(Mandatory)][string]$Container,
    [Parameter(Mandatory)][string]$Label,
    [int]$IntervalSeconds = 1
)

$outDir = "bench-logs"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$log = Join-Path $outDir "$Label-docker.csv"
"timestamp,cpu_perc,mem_usage,mem_perc" | Out-File -FilePath $log -Encoding utf8

Write-Host "Logueando $Container cada $IntervalSeconds s. Ctrl+C para cortar."
while ($true) {
    $stats = docker stats $Container --no-stream --format "{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}}"
    "$(Get-Date -Format o),$stats" | Out-File -FilePath $log -Append -Encoding utf8
    Start-Sleep -Seconds $IntervalSeconds
}