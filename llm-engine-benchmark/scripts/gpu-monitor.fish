#!/usr/bin/env fish

if test (count $argv) -ne 1
    echo "Uso: gpu-monitor.fish ARCHIVO_CSV" >&2
    exit 2
end

set log $argv[1]

if not type -q nvidia-smi
    echo "Error: nvidia-smi no está disponible." >&2
    exit 1
end

if not nvidia-smi \
    --query-gpu=utilization.gpu,utilization.memory,memory.used,memory.total \
    --format=csv,noheader,nounits \
    >/dev/null 2>&1
    echo "Error: no se pudo consultar la GPU NVIDIA." >&2
    exit 1
end

mkdir -p (dirname -- "$log")

echo 'timestamp_ns,gpu_perc,memory_util_perc,memory_used_mib,memory_total_mib' > "$log"

echo "Monitoreando GPU -> $log"
echo "Intervalo: 100 ms"
echo "Ctrl+C para detener."

while true
    set timestamp (date +%s%N)

    set stats (
        nvidia-smi \
        --query-gpu=utilization.gpu,utilization.memory,memory.used,memory.total \
        --format=csv,noheader,nounits \
        2>/dev/null
    )

    set stats_status $status

    if test $stats_status -ne 0
        echo "Error: nvidia-smi terminó con código $stats_status." >&2
        exit $stats_status
    end

    if test -n "$stats"
        printf '%s,%s\n' "$timestamp" "$stats" >> "$log"
    end

    sleep 0.1
end