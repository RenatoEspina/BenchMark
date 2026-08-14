#!/usr/bin/env fish

if test (count $argv) -ne 2
    echo "Uso: docker-monitor.fish CONTENEDOR ARCHIVO_CSV" >&2
    exit 2
end

set container $argv[1]
set log $argv[2]

mkdir -p (dirname -- "$log")
echo 'timestamp,cpu_perc,mem_usage,mem_perc' > "$log"

while docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null | string match -q '^true$'
    printf '%s,' (date --iso-8601=seconds) >> "$log"
    docker stats "$container" --no-stream \
        --format '{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}}' \
        >> "$log" 2>/dev/null
    sleep 1
end