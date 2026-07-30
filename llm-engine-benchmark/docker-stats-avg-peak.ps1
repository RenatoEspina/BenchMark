param(
    [Parameter(Mandatory)][string]$CsvPath
)

function Convert-ToMb {
    param([string]$Value)
    $Value = $Value.Trim()
    if ($Value -match '^([\d.]+)\s*GiB$') {
        return [double]$matches[1] * 1024
    } elseif ($Value -match '^([\d.]+)\s*MiB$') {
        return [double]$matches[1]
    } elseif ($Value -match '^([\d.]+)\s*KiB$') {
        return [double]$matches[1] / 1024
    } elseif ($Value -match '^([\d.]+)\s*B$') {
        return [double]$matches[1] / 1MB
    } else {
        return $null
    }
}

$rows = Import-Csv -Path $CsvPath

$usedMb = @()
$cpuPercents = @()
foreach ($row in $rows) {
    if ($row.mem_usage) {
        $parts = $row.mem_usage -split '/'
        if ($parts.Count -ge 1) {
            $used = Convert-ToMb $parts[0]
            if ($used -ne $null) { $usedMb += $used }
        }
    }
    if ($row.cpu_perc -match '^([\d.]+)%?$') {
        $cpuPercents += [double]$matches[1]
    }
}

if ($usedMb.Count -eq 0) {
    Write-Host "No se pudieron parsear valores de mem_usage en $CsvPath"
    return
}

$avgMem = [math]::Round(($usedMb | Measure-Object -Average).Average, 1)
$peakMem = [math]::Round(($usedMb | Measure-Object -Maximum).Maximum, 1)

Write-Host "Muestras RAM: $($usedMb.Count)"
Write-Host "RAM promedio (contenedor): $avgMem MB"
Write-Host "RAM pico (contenedor): $peakMem MB"

if ($cpuPercents.Count -gt 0) {
    $avgCpu = [math]::Round(($cpuPercents | Measure-Object -Average).Average, 1)
    $peakCpu = [math]::Round(($cpuPercents | Measure-Object -Maximum).Maximum, 1)
    Write-Host ""
    Write-Host "CPU% promedio (contenedor): $avgCpu %"
    Write-Host "CPU% pico (contenedor): $peakCpu %"
}