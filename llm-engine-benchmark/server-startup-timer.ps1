param(
    [Parameter(Mandatory)][string]$Label,
    [Parameter(Mandatory)][string]$HealthUrl,
    [Parameter(Mandatory)][string]$Command,
    [string[]]$CommandArgs,
    [int]$TimeoutSeconds = 300,
    [int]$PollMs = 200
)

$outDir = "bench-logs"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Write-Host "Iniciando: $Command $($CommandArgs -join ' ')"
$proc = Start-Process $Command -ArgumentList $CommandArgs -PassThru -NoNewWindow

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$ready = $false
while ($sw.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
    if ($proc.HasExited) {
        Write-Host "El proceso termino inesperadamente (codigo $($proc.ExitCode)) antes de quedar listo."
        return
    }
    try {
        $response = Invoke-WebRequest -Uri $HealthUrl -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            $ready = $true
            break
        }
    } catch {
    }
    Start-Sleep -Milliseconds $PollMs
}
$sw.Stop()

if ($ready) {
    Write-Host ""
    Write-Host "=== Tiempo de carga del servidor ($Label) ==="
    Write-Host "Tiempo hasta health check OK: $([math]::Round($sw.Elapsed.TotalMilliseconds,1)) ms"
    $log = Join-Path $outDir "$Label-startup.csv"
    "label,startup_ms" | Out-File -FilePath $log -Encoding utf8
    "$Label,$([math]::Round($sw.Elapsed.TotalMilliseconds,1))" | Out-File -FilePath $log -Append -Encoding utf8
    Write-Host "Log en: $log"
} else {
    Write-Host "Timeout: el servidor no respondio $HealthUrl dentro de $TimeoutSeconds s."
}

Write-Host ""
Write-Host "Servidor sigue corriendo (PID $($proc.Id)). Presiona Ctrl+C para detenerlo cuando termines las pruebas."
$proc.WaitForExit()