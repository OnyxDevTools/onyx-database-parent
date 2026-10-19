#!/usr/bin/env python3
"""Compare legacy row Futures, a core-sized pool, and the current chunked processor in isolated JVMs."""

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cores", type=int, default=4,
                        help="ActiveProcessorCount and, on Linux, CPU affinity per isolated benchmark JVM")
    parser.add_argument("--rows", type=int, nargs="+", default=[1200, 20000])
    parser.add_argument("--concurrent", type=int, nargs="+", default=[1, 4])
    parser.add_argument("--work", type=int, nargs="+", default=[0, 128])
    parser.add_argument("--warmups", type=int, default=8)
    parser.add_argument("--rounds", type=int, default=20)
    parser.add_argument("--forks", type=int, default=3)
    parser.add_argument("--output", type=Path, help="Write raw JSON lines here as well as stdout")
    args = parser.parse_args()
    if min(args.cores, args.warmups, args.rounds, args.forks, *args.rows, *args.concurrent) <= 0 or min(args.work) < 0:
        parser.error("Counts must be positive, and work must be nonnegative")
    root = Path(__file__).resolve().parents[1]
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.environ.get("JAVA_HOME") else shutil.which("java")
    if not java:
        parser.error("Select JDK 23 through JAVA_HOME or PATH")
    affinity = sorted(os.sched_getaffinity(0))[:args.cores] if hasattr(os, "sched_getaffinity") else None
    if affinity is not None and len(affinity) < args.cores:
        parser.error(f"Only {len(affinity)} CPUs are available in the current affinity mask")

    def child_setup():
        if affinity is not None:
            os.sched_setaffinity(0, affinity)

    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"

    def jar(group, module, version):
        candidates = sorted(path for path in (cache / group / module / version).glob("*/*.jar")
                            if not path.name.endswith(("-sources.jar", "-javadoc.jar")))
        if not candidates:
            parser.error(f"Missing cached {module}:{version}; compile the project first")
        return str(candidates[0])

    runtime = [jar("org.jetbrains.kotlin", "kotlin-stdlib", "2.2.10")]
    compiler = runtime + [
        jar("org.jetbrains.kotlin", "kotlin-reflect", "2.2.10"),
        jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.2.10"),
        jar("org.jetbrains.kotlin", "kotlin-script-runtime", "2.2.10"),
        jar("org.jetbrains.kotlin", "kotlin-daemon-embeddable", "2.2.10"),
        jar("org.jetbrains.intellij.deps", "trove4j", "1.0.20200330"),
        jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"),
        jar("org.jetbrains", "annotations", "23.0.0"),
    ]
    output_file = args.output.open("w", encoding="utf-8") if args.output else None
    try:
        with tempfile.TemporaryDirectory(prefix="onyx-row-scheduling-") as temporary:
            output = Path(temporary) / "classes"
            subprocess.run([
                java, "-Xmx512m", "-XX:ActiveProcessorCount=2", "-cp", os.pathsep.join(compiler),
                "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "23",
                "-classpath", os.pathsep.join(runtime), "-d", str(output),
                str(root / "onyx-database/src/main/kotlin/com/onyx/extension/common/ParallelRowProcessor.kt"),
                str(root / "benchmarks/QueryRowSchedulingBenchmark.kt"),
            ], cwd=root, check=True, timeout=120, preexec_fn=child_setup if affinity is not None else None)
            for rows in args.rows:
                for concurrent in args.concurrent:
                    for work in args.work:
                        for fork in range(args.forks):
                            # Rotate order between forks to reduce background-load bias.
                            modes = ["legacy", "bounded-per-row", "chunked"]
                            modes = modes[fork % 3:] + modes[:fork % 3]
                            for mode in modes:
                                result = subprocess.check_output([
                                    java, "-Xms64m", "-Xmx256m", f"-XX:ActiveProcessorCount={args.cores}",
                                    "-cp", os.pathsep.join([str(output), *runtime]),
                                    "com.onyx.extension.common.QueryRowSchedulingBenchmark", mode, str(rows),
                                    str(concurrent), str(work), str(args.warmups), str(args.rounds),
                                ], cwd=root, text=True, timeout=180,
                                    preexec_fn=child_setup if affinity is not None else None)
                                record = json.loads(result)
                                record["fork"] = fork
                                record["cpu_affinity"] = affinity
                                line = json.dumps(record)
                                print(line, flush=True)
                                if output_file:
                                    output_file.write(line + "\n")
                                    output_file.flush()
    finally:
        if output_file:
            output_file.close()


if __name__ == "__main__":
    main()
