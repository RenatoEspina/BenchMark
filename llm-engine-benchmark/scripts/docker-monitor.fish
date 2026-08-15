#!/usr/bin/env fish

if test (count $argv) -ne 2
    echo "Uso: docker-monitor.fish CONTENEDOR ARCHIVO_CSV" >&2
    exit 2
end

set container $argv[1]
set log $argv[2]

if not docker inspect "$container" >/dev/null 2>&1
    echo "Error: el contenedor '$container' no existe." >&2
    exit 1
end

set running (docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null)

if test "$running" != "true"
    echo "Error: el contenedor '$container' no está ejecutándose." >&2
    exit 1
end

mkdir -p (dirname -- "$log")
echo 'timestamp,cpu_perc,mem_usage,mem_perc' > "$log"

echo "Monitoreando '$container' -> $log"
echo "Ctrl+C para detener."

docker stats "$container" \
    --format '{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}}' \
    2>/dev/null |
while read -l stats
    if test -n "$stats"
        printf '%s,%s\n' (date --iso-8601=ns) "$stats" >> "$log"
    end
end

set pipeline_status $pipestatus

if test $pipeline_status[1] -ne 0
    echo "Error: docker stats terminó con código $pipeline_status[1]." >&2
    exit $pipeline_status[1]
end