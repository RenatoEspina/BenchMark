#!/usr/bin/env fish

if test (count $argv) -ne 1
    echo "Uso: gpu-monitor.fish ARCHIVO_CSV" >&2
    exit 2
end

set log $argv[1]

mkdir -p (dirname -- "$log")

nvidia-smi \
    --query-gpu=timestamp,utilization.gpu,utilization.memory,memory.used,memory.total \
    --format=csv -lms 200 \
    | tee "$log"