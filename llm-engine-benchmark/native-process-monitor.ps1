param(
    [Parameter(Mandatory)][string]$ProcessName,
    [Parameter(Mandatory)][string]$Label,
    [int]$IntervalSeconds = 1
)

$outDir = "bench-logs"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$log = Join-Path $outDir "$Label-proc.csv"
"timestamp,cpu_percent,workingset_mb,privatemem_mb" | Out-File -FilePath $log -Encoding utf8

$counterPath = "\Process($ProcessName*)\% Processor Time"

Write-Host "Logueando '$ProcessName' cada $IntervalSeconds s. Ctrl+C para cortar."
while ($true) {
    $cpuPercent = 0
    try {
        $sample = (Get-Counter $counterPath -ErrorAction Stop).CounterSamples |
            Measure-Object -Property CookedValue -Sum
        $cpuPercent = [math]::Round($sample.Sum, 1)
    } catch {}

    $p = Get-Process -Name $ProcessName -ErrorAction SilentlyContinue
    if ($p) {
        $ws = [math]::Round(($p | Measure-Object WorkingSet64 -Sum).Sum / 1MB, 1)
        $pm = [math]::Round(($p | Measure-Object PrivateMemorySize64 -Sum).Sum / 1MB, 1)
        "$(Get-Date -Format o),$cpuPercent,$ws,$pm" | Out-File -FilePath $log -Append -Encoding utf8
    }
    Start-Sleep -Seconds $IntervalSeconds
}