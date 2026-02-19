#!/usr/bin/env python3
"""Generate optimization heatmap and code-size reduction visuals for website use.

Outputs under artifacts/metrics:
- optimization-pass-heatmap.png
- code-size-reduction.png
- instruction_counts.csv
- pass_activity.csv
- summary.txt
"""

from __future__ import annotations

import csv
import re
import shutil
import subprocess
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import matplotlib.pyplot as plt
import numpy as np


PASSES = ["CF", "CP", "CPP", "DCE", "CSE", "OFE"]


def count_dlx_instructions(asm_file: Path) -> Optional[int]:
    if not asm_file.exists():
        return None
    pattern = re.compile(r"^\d+:\s")
    count = 0
    with asm_file.open("r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            if pattern.match(line):
                count += 1
    return count


def parse_pass_counts(record_file: Path) -> Dict[str, int]:
    counts = {p: 0 for p in PASSES}
    if not record_file.exists():
        return counts

    pass_pat = re.compile(r"\b(" + "|".join(PASSES) + r"):" )
    with record_file.open("r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            m = pass_pat.search(line)
            if m:
                counts[m.group(1)] += 1
    return counts


def run_baseline_codegen(
    root: Path,
    tests: List[Path],
    baseline_dir: Path,
) -> Tuple[Dict[str, Optional[int]], Dict[str, int]]:
    classes = root / "target" / "classes"
    cli_jar = root / "third_party" / "lib" / "commons-cli-1.9.0.jar"

    baseline_counts: Dict[str, Optional[int]] = {}
    exit_codes: Dict[str, int] = {}

    if baseline_dir.exists():
        shutil.rmtree(baseline_dir)
    baseline_dir.mkdir(parents=True, exist_ok=True)

    for test_file in tests:
        base = test_file.stem
        tmp_src = baseline_dir / f"{base}.txt"
        shutil.copy2(test_file, tmp_src)

        preferred_in = test_file.with_suffix(".in")
        input_file = preferred_in if preferred_in.exists() else (root / "tests" / "dummy.in")

        cmd = [
            "java",
            "-cp",
            f"{classes}:{cli_jar}",
            "mocha.CompilerTester",
            "-s",
            str(tmp_src),
            "-i",
            str(input_file),
            "-b",
        ]

        proc = subprocess.run(
            cmd,
            cwd=baseline_dir,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
        exit_codes[base] = proc.returncode

        asm_file = baseline_dir / f"{base}_asm.txt"
        baseline_counts[base] = count_dlx_instructions(asm_file)

    return baseline_counts, exit_codes


def write_instruction_csv(
    out_csv: Path,
    test_names: List[str],
    baseline: Dict[str, Optional[int]],
    optimized: Dict[str, Optional[int]],
    baseline_exit: Dict[str, int],
) -> None:
    with out_csv.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(
            [
                "test",
                "baseline_instr",
                "optimized_instr",
                "reduction_instr",
                "reduction_pct",
                "baseline_exit_code",
            ]
        )

        for t in test_names:
            b = baseline.get(t)
            o = optimized.get(t)
            reduction_abs = ""
            reduction_pct = ""

            if b is not None and o is not None and b > 0:
                reduction_abs = b - o
                reduction_pct = round((b - o) * 100.0 / b, 2)

            writer.writerow([
                t,
                "" if b is None else b,
                "" if o is None else o,
                reduction_abs,
                reduction_pct,
                baseline_exit.get(t, ""),
            ])


def write_pass_csv(
    out_csv: Path,
    test_names: List[str],
    pass_counts: Dict[str, Dict[str, int]],
) -> None:
    with out_csv.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["test", *PASSES, "total_rewrites"])
        for t in test_names:
            row = [pass_counts[t][p] for p in PASSES]
            writer.writerow([t, *row, sum(row)])


def plot_heatmap(
    out_png: Path,
    ordered_tests: List[str],
    pass_counts: Dict[str, Dict[str, int]],
) -> None:
    matrix = np.array([[pass_counts[t][p] for p in PASSES] for t in ordered_tests], dtype=float)

    fig_height = max(7.0, 0.24 * len(ordered_tests) + 2.0)
    fig, ax = plt.subplots(figsize=(11, fig_height))

    im = ax.imshow(matrix, aspect="auto", cmap="YlOrRd")
    cbar = fig.colorbar(im, ax=ax)
    cbar.set_label("Transformation count")

    ax.set_xticks(range(len(PASSES)))
    ax.set_xticklabels(PASSES)
    ax.set_yticks(range(len(ordered_tests)))
    ax.set_yticklabels(ordered_tests, fontsize=7)
    ax.set_title("Optimization Activity Heatmap (per test, max mode)")
    ax.set_xlabel("Optimization pass")
    ax.set_ylabel("Test case")

    fig.tight_layout()
    fig.savefig(out_png, dpi=220)
    plt.close(fig)


def plot_reduction_chart(
    out_png: Path,
    test_names: List[str],
    baseline: Dict[str, Optional[int]],
    optimized: Dict[str, Optional[int]],
) -> Tuple[int, int, float]:
    rows = []
    total_base = 0
    total_opt = 0

    for t in test_names:
        b = baseline.get(t)
        o = optimized.get(t)
        if b is None or o is None or b <= 0:
            continue
        pct = (b - o) * 100.0 / b
        rows.append((t, b, o, b - o, pct))
        total_base += b
        total_opt += o

    rows.sort(key=lambda r: r[3], reverse=True)
    top = rows[:25]

    labels = [r[0] for r in top][::-1]
    pct_vals = [r[4] for r in top][::-1]

    fig_h = max(7.0, 0.3 * len(top) + 2.5)
    fig, ax = plt.subplots(figsize=(12, fig_h))
    bars = ax.barh(labels, pct_vals, color="#2a9d8f")
    ax.axvline(0.0, color="black", linewidth=0.8)
    ax.set_xlabel("Code-size reduction (%)")
    ax.set_title("Generated DLX Code Reduction by Test (Top 25 by instruction drop)")

    for bar, val in zip(bars, pct_vals):
        ax.text(val + (0.8 if val >= 0 else -0.8), bar.get_y() + bar.get_height() / 2.0, f"{val:.1f}%", va="center", fontsize=8)

    if total_base > 0:
        overall_pct = (total_base - total_opt) * 100.0 / total_base
        summary = (
            f"Overall: {total_base} -> {total_opt} instructions "
            f"({total_base - total_opt} fewer, {overall_pct:.2f}% reduction)"
        )
        fig.text(0.5, 0.01, summary, ha="center", fontsize=10)
    else:
        overall_pct = 0.0

    fig.tight_layout(rect=[0.0, 0.03, 1.0, 1.0])
    fig.savefig(out_png, dpi=220)
    plt.close(fig)

    return total_base, total_opt, overall_pct


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    tests_dir = root / "tests"
    artifacts = root / "artifacts"
    asm_dir = artifacts / "asm"
    records_dir = artifacts / "records"
    metrics_dir = artifacts / "metrics"
    baseline_tmp = metrics_dir / "_baseline_tmp"

    metrics_dir.mkdir(parents=True, exist_ok=True)

    subprocess.run([str(root / "scripts" / "build.sh")], cwd=root, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    tests = sorted(tests_dir.glob("test*.txt"), key=lambda p: p.stem)
    test_names = [t.stem for t in tests]

    optimized_counts = {t: count_dlx_instructions(asm_dir / f"{t}_asm.txt") for t in test_names}
    pass_counts = {t: parse_pass_counts(records_dir / f"record_{t}_max.txt") for t in test_names}

    baseline_counts, baseline_exit = run_baseline_codegen(root, tests, baseline_tmp)

    instruction_csv = metrics_dir / "instruction_counts.csv"
    pass_csv = metrics_dir / "pass_activity.csv"
    heatmap_png = metrics_dir / "optimization-pass-heatmap.png"
    reduction_png = metrics_dir / "code-size-reduction.png"
    summary_txt = metrics_dir / "summary.txt"

    write_instruction_csv(instruction_csv, test_names, baseline_counts, optimized_counts, baseline_exit)
    write_pass_csv(pass_csv, test_names, pass_counts)

    ordered_tests = sorted(test_names, key=lambda t: sum(pass_counts[t][p] for p in PASSES), reverse=True)
    plot_heatmap(heatmap_png, ordered_tests, pass_counts)
    total_base, total_opt, overall_pct = plot_reduction_chart(reduction_png, test_names, baseline_counts, optimized_counts)

    missing_opt = sum(1 for t in test_names if optimized_counts[t] is None)
    missing_base = sum(1 for t in test_names if baseline_counts[t] is None)

    with summary_txt.open("w", encoding="utf-8") as f:
        f.write("Website Metrics Summary\n")
        f.write("=======================\n")
        f.write(f"Tests considered: {len(test_names)}\n")
        f.write(f"Missing optimized asm files: {missing_opt}\n")
        f.write(f"Missing baseline asm files: {missing_base}\n")
        f.write(f"Total baseline instructions: {total_base}\n")
        f.write(f"Total optimized instructions: {total_opt}\n")
        f.write(f"Overall code-size reduction: {overall_pct:.2f}%\n")
        f.write("\nOutput files:\n")
        f.write(f"- {heatmap_png}\n")
        f.write(f"- {reduction_png}\n")
        f.write(f"- {instruction_csv}\n")
        f.write(f"- {pass_csv}\n")

    if baseline_tmp.exists():
        shutil.rmtree(baseline_tmp)

    print(f"Wrote: {heatmap_png}")
    print(f"Wrote: {reduction_png}")
    print(f"Wrote: {instruction_csv}")
    print(f"Wrote: {pass_csv}")
    print(f"Wrote: {summary_txt}")


if __name__ == "__main__":
    main()
