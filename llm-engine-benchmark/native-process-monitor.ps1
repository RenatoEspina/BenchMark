# native-process-monitor.ps1
param(
    [Parameter(Mandatory)][string]$ProcessName,
    [Parameter(Mandatory)][string]$Label,
    [int]$IntervalSeconds = 1
)

$outDir = "bench-logs"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$log = Join-Path $outDir "$Label-proc.csv"
"timestamp,workingset_mb,privatemem_mb" | Out-File -FilePath $log -Encoding utf8

Write-Host "Logueando proceso '$ProcessName' cada $IntervalSeconds s. Ctrl+C para cortar."
while ($true) {
    $p = Get-Process -Name $ProcessName -ErrorAction SilentlyContinue
    if ($p) {
        $ws = [math]::Round(($p | Measure-Object WorkingSet64 -Sum).Sum / 1MB, 1)
        $pm = [math]::Round(($p | Measure-Object PrivateMemorySize64 -Sum).Sum / 1MB, 1)
        "$(Get-Date -Format o),$ws,$pm" | Out-File -FilePath $log -Append -Encoding utf8
    }
    Start-Sleep -Seconds $IntervalSeconds
}