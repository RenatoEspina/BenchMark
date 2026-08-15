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
    --query-gpu=timestamp,utilization.gpu,utilization.memory,memory.used,memory.total \
    --format=csv,noheader \
    >/dev/null 2>&1
    echo "Error: no se pudo consultar la GPU NVIDIA." >&2
    exit 1
end

mkdir -p (dirname -- "$log")

echo "Monitoreando GPU -> $log"
echo "Ctrl+C para detener."

nvidia-smi \
    --query-gpu=timestamp,utilization.gpu,utilization.memory,memory.used,memory.total \
    --format=csv \
    -lms 200 \
    | tee "$log"

set pipeline_status $pipestatus

if test $pipeline_status[1] -ne 0
    echo "Error: nvidia-smi terminó con código $pipeline_status[1]." >&2
    exit $pipeline_status[1]
end