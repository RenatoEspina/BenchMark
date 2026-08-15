#!/usr/bin/env python3

import argparse
import csv
import math
import re
import statistics
from datetime import datetime
from pathlib import Path


def number(value):
    match = re.search(r"[-+]?\d+(?:\.\d+)?", value or "")
    return float(match.group()) if match else math.nan


def mib(value):
    n = number(value)
    if math.isnan(n):
        return n
    s = (value or "").lower()
    if "gib" in s:
        return n * 1024
    if "kib" in s:
        return n / 1024
    if "tib" in s:
        return n * 1024 * 1024
    if "gb" in s:
        return n * 1000 / 1024
    if "mb" in s:
        return n * 1000 * 1000 / (1024 * 1024)
    if "kb" in s:
        return n * 1000 / (1024 * 1024)
    if re.search(r"\bb\b", s):
        return n / (1024 * 1024)
    return n


def percentile(values, q):
    values = sorted(v for v in values if not math.isnan(v))
    if not values:
        return math.nan
    if len(values) == 1:
        return values[0]
    pos = (len(values) - 1) * q
    lo = math.floor(pos)
    hi = math.ceil(pos)
    if lo == hi:
        return values[lo]
    return values[lo] + (values[hi] - values[lo]) * (pos - lo)


def mean(values):
    values = [v for v in values if not math.isnan(v)]
    return statistics.fmean(values) if values else math.nan


def maximum(values):
    values = [v for v in values if not math.isnan(v)]
    return max(values) if values else math.nan


def parse_docker_time(value):
    return int(datetime.fromisoformat(value.strip()).timestamp() * 1_000_000_000)


def parse_gpu_time(value):
    value = value.strip()
    for fmt in ("%Y/%m/%d %H:%M:%S.%f", "%Y/%m/%d %H:%M:%S"):
        try:
            return int(datetime.strptime(value, fmt).timestamp() * 1_000_000_000)
        except ValueError:
            pass
    raise ValueError(f"Timestamp NVIDIA no reconocido: {value}")


def read_window(path):
    with open(path, newline="") as f:
        rows = list(csv.DictReader(f))
    if len(rows) != 1:
        raise ValueError("El archivo de ventana debe contener exactamente una ejecución.")
    start = int(rows[0]["start_ns"])
    end = int(rows[0]["end_ns"])
    if end <= start:
        raise ValueError("La ventana de medición no es válida.")
    return start, end


def read_docker(path):
    rows = []
    with open(path, newline="") as f:
        for row in csv.DictReader(f):
            try:
                ts = parse_docker_time(row["timestamp"])
            except Exception:
                continue
            cpu = number(row.get("cpu_perc", ""))
            usage = row.get("mem_usage", "")
            used = mib(usage.split("/")[0])
            rows.append((ts, cpu, used))
    return rows


def read_gpu(path):
    rows = []
    with open(path, newline="") as f:
        reader = csv.reader(f)
        next(reader, None)
        for row in reader:
            if len(row) < 5:
                continue
            try:
                ts = parse_gpu_time(row[0])
            except Exception:
                continue
            rows.append((ts, number(row[1]), mib(row[3])))
    return rows


def select(rows, start, end):
    return [r for r in rows if start <= r[0] <= end]


def baseline(rows, start, seconds):
    lo = start - int(seconds * 1_000_000_000)
    return [r for r in rows if lo <= r[0] < start]


def activity_stats(rows, baseline_rows, index, floor, margin):
    vals = [r[index] for r in rows if not math.isnan(r[index])]
    base_vals = [r[index] for r in baseline_rows if not math.isnan(r[index])]
    base_p95 = percentile(base_vals, 0.95)
    threshold = floor if math.isnan(base_p95) else max(floor, base_p95 + margin)
    active = [r for r in rows if not math.isnan(r[index]) and r[index] >= threshold]
    ratio = (100 * len(active) / len(vals)) if vals else math.nan
    span = 0.0
    if len(active) >= 2:
        span = (active[-1][0] - active[0][0]) / 1_000_000_000
    elif len(active) == 1:
        span = 0.0
    return threshold, ratio, span


def fmt(value, digits=2):
    return "N/D" if math.isnan(value) else f"{value:.{digits}f}"


def report_docker(rows, start, end, baseline_seconds, floor, margin):
    measured = select(rows, start, end)
    base = baseline(rows, start, baseline_seconds)

    if not measured:
        print("Docker: sin muestras dentro de la ventana medida.")
        return

    cpu = [r[1] for r in measured]
    ram = [r[2] for r in measured]
    base_cpu = [r[1] for r in base]
    base_ram = [r[2] for r in base]

    threshold, ratio, span = activity_stats(measured, base, 1, floor, margin)
    ram_base = mean(base_ram)
    ram_peak = maximum(ram)
    ram_delta = math.nan if math.isnan(ram_base) or math.isnan(ram_peak) else max(0.0, ram_peak - ram_base)

    print("Docker CPU/RAM")
    print(f"Muestras ventana: {len(measured)}")
    print(f"CPU promedio ventana: {fmt(mean(cpu))} %")
    print(f"CPU pico ventana: {fmt(maximum(cpu))} %")
    print(f"CPU baseline previo: {fmt(mean(base_cpu))} %")
    print(f"Umbral actividad CPU: {fmt(threshold)} %")
    print(f"Muestras CPU activas: {fmt(ratio)} %")
    print(f"Tramo CPU activo detectado: {fmt(span, 3)} s")
    print(f"RAM promedio ventana: {fmt(mean(ram), 1)} MiB")
    print(f"RAM pico ventana: {fmt(ram_peak, 1)} MiB")
    print(f"RAM baseline previo: {fmt(ram_base, 1)} MiB")
    print(f"RAM incremento pico sobre baseline: {fmt(ram_delta, 1)} MiB")


def report_gpu(rows, start, end, baseline_seconds, floor, margin):
    measured = select(rows, start, end)
    base = baseline(rows, start, baseline_seconds)

    if not measured:
        print("GPU: sin muestras dentro de la ventana medida.")
        return

    gpu = [r[1] for r in measured]
    vram = [r[2] for r in measured]
    base_gpu = [r[1] for r in base]
    base_vram = [r[2] for r in base]

    threshold, ratio, span = activity_stats(measured, base, 1, floor, margin)
    vram_base = mean(base_vram)
    vram_peak = maximum(vram)
    vram_delta = math.nan if math.isnan(vram_base) or math.isnan(vram_peak) else max(0.0, vram_peak - vram_base)

    print("GPU/VRAM")
    print(f"Muestras ventana: {len(measured)}")
    print(f"GPU promedio ventana: {fmt(mean(gpu))} %")
    print(f"GPU pico ventana: {fmt(maximum(gpu))} %")
    print(f"GPU baseline previo: {fmt(mean(base_gpu))} %")
    print(f"Umbral actividad GPU: {fmt(threshold)} %")
    print(f"Muestras GPU activas: {fmt(ratio)} %")
    print(f"Tramo GPU activo detectado: {fmt(span, 3)} s")
    print(f"VRAM promedio ventana: {fmt(mean(vram), 1)} MiB")
    print(f"VRAM pico ventana: {fmt(vram_peak, 1)} MiB")
    print(f"VRAM baseline previo: {fmt(vram_base, 1)} MiB")
    print(f"VRAM incremento pico sobre baseline: {fmt(vram_delta, 1)} MiB")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--window", required=True)
    parser.add_argument("--docker")
    parser.add_argument("--gpu")
    parser.add_argument("--baseline-seconds", type=float, default=5.0)
    parser.add_argument("--activity-floor", type=float, default=5.0)
    parser.add_argument("--activity-margin", type=float, default=5.0)
    args = parser.parse_args()

    start, end = read_window(args.window)
    print(f"Ventana medida: {(end - start) / 1_000_000_000:.3f} s")

    if args.docker:
        report_docker(
            read_docker(args.docker),
            start,
            end,
            args.baseline_seconds,
            args.activity_floor,
            args.activity_margin,
        )

    if args.gpu:
        if args.docker:
            print()
        report_gpu(
            read_gpu(args.gpu),
            start,
            end,
            args.baseline_seconds,
            args.activity_floor,
            args.activity_margin,
        )


if __name__ == "__main__":
    main()
