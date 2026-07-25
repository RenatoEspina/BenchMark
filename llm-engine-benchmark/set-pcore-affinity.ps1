param(
    [Parameter(Mandatory)][string]$ProcessName,
    [int64]$AffinityMask = 0xFFF
)

$procs = Get-Process -Name $ProcessName -ErrorAction SilentlyContinue
if (-not $procs) {
    Write-Host "No se encontro proceso '$ProcessName' todavia."
    return
}
foreach ($p in $procs) {
    try {
        $p.ProcessorAffinity = [IntPtr]$AffinityMask
        Write-Host "Afinidad P-cores aplicada a $($p.ProcessName) (PID $($p.Id))"
    } catch {
        Write-Host "No se pudo fijar afinidad en PID $($p.Id): $_"
    }
}