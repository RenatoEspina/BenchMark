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

set pid (docker inspect --format '{{.State.Pid}}' "$container")

if test -z "$pid"; or test "$pid" = "0"
    echo "Error: no se pudo obtener el PID del contenedor." >&2
    exit 1
end

set cgroup_relative (awk -F: '$1 == "0" { print $3 }' /proc/$pid/cgroup)
set cgroup_path "/sys/fs/cgroup$cgroup_relative"

if not test -r "$cgroup_path/cpu.stat"
    echo "Error: no se puede leer $cgroup_path/cpu.stat" >&2
    exit 1
end

if not test -r "$cgroup_path/memory.current"
    echo "Error: no se puede leer $cgroup_path/memory.current" >&2
    exit 1
end

mkdir -p (dirname -- "$log")

echo 'timestamp_ns,cpu_perc,mem_usage,mem_perc' > "$log"

echo "Monitoreando '$container' -> $log"
echo "Intervalo: 100 ms"
echo "Ctrl+C para detener."

set previous_cpu (awk '$1 == "usage_usec" { print $2 }' "$cgroup_path/cpu.stat")
set previous_time (date +%s%N)

while true
    set running (docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null)

    if test "$running" != "true"
        break
    end

    sleep 0.1

    set current_time (date +%s%N)
    set current_cpu (awk '$1 == "usage_usec" { print $2 }' "$cgroup_path/cpu.stat")
    set memory_bytes (cat "$cgroup_path/memory.current")

    set delta_cpu (math "$current_cpu - $previous_cpu")
    set delta_time (math "$current_time - $previous_time")

    if test "$delta_time" -gt 0
        set cpu_percent (math -s 2 "$delta_cpu * 100000 / $delta_time")
        set memory_mib (math -s 2 "$memory_bytes / 1048576")

        printf '%s,%s%%,%sMiB,N/A\n' \
            "$current_time" \
            "$cpu_percent" \
            "$memory_mib" \
            >> "$log"
    end

    set previous_cpu $current_cpu
    set previous_time $current_time
end