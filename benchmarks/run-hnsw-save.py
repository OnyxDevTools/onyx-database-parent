#!/usr/bin/env python3
"""Run serial, alternating JVM forks of the HNSW save/cache benchmark (JDK 23)."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import statistics
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--forks", type=int, default=3)
    parser.add_argument("--rows", type=int, default=12_000)
    parser.add_argument("--measured", type=int, default=4_000)
    parser.add_argument("--dimensions", type=int, default=384)
    parser.add_argument("--kinds", nargs="+", choices=["random", "clustered"], default=["random", "clustered"])
    parser.add_argument("--storage", nargs="+", choices=["memory", "mapped"], default=["memory", "mapped"])
    parser.add_argument("--capacity", type=int, help="Override onyx.hnsw.nodeCacheCapacity in benchmark children only")
    parser.add_argument("--caches", nargs="+", choices=["lru", "weak", "production"], default=["lru", "production"])
    parser.add_argument("--baseline-classpath", help="Optional pre-change main classes, prepended in separate baseline forks")
    parser.add_argument("--output", type=Path, default=Path("/tmp/onyx-hnsw-save-benchmark.json"))
    args = parser.parse_args()
    if args.forks < 1 or not 0 < args.measured < args.rows or args.rows < 256:
        parser.error("Use positive forks, rows >= 256, and 0 < measured < rows")
    root = Path(__file__).resolve().parents[1]
    java_home = Path(os.environ["JAVA_HOME"]) if "JAVA_HOME" in os.environ else Path(shutil.which("java")).resolve().parents[1]
    java = str(java_home / "bin/java")
    env = dict(os.environ, JAVA_HOME=str(java_home))
    with tempfile.TemporaryDirectory(prefix="hnsw-benchmark-build-") as temporary:
        temporary = Path(temporary)
        init = temporary / "classpath.gradle"
        classpath_file = temporary / "classpath.txt"
        init.write_text('''gradle.projectsEvaluated {
    def database = gradle.rootProject.findProject(":onyx-database")
    if (database == null) return
    database.tasks.register("writeHnswBenchmarkClasspath") {
        dependsOn(database.tasks.named("testClasses"))
        doLast {
            new File(System.getProperty("hnsw.benchmark.classpathFile")).text =
                database.sourceSets.test.runtimeClasspath.asPath
        }
    }
}
''')
        subprocess.run([
            str(root / "gradlew"), "--console=plain", "-I", str(init),
            f"-Dhnsw.benchmark.classpathFile={classpath_file}",
            ":onyx-database:writeHnswBenchmarkClasspath",
        ], cwd=root, env=env, check=True)
        classpath = classpath_file.read_text()
    java_version = subprocess.run([java, "-version"], capture_output=True, text=True, check=True).stderr.strip()
    source = root / "onyx-database/src/main/kotlin/com/onyx/interactors/index/impl/PersistentHnswIndex.kt"
    report = {
        "java": java_version,
        "jvm_args": ["-Xms512m", "-Xmx2g", "-XX:-UseCompressedOops", "-XX:ActiveProcessorCount=8"],
        "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "clock_source_sha256": hashlib.sha256((root / "onyx-database/src/main/kotlin/com/onyx/lang/map/ConcurrentClockCache.kt").read_bytes()).hexdigest(),
        "parameters": {key: str(value) if isinstance(value, Path) else value for key, value in vars(args).items()},
        "runs": [],
    }
    if args.capacity is not None:
        report["jvm_args"].append(f"-Donyx.hnsw.nodeCacheCapacity={args.capacity}")
    variants = args.caches.copy()
    if args.baseline_classpath:
        variants.insert(0, "baseline")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    expected = {}
    for storage in args.storage:
        for kind in args.kinds:
            for fork in range(args.forks):
                # Rotate so one implementation does not always run first/last.
                order = variants[fork % len(variants):] + variants[:fork % len(variants)]
                for variant in order:
                    cp = classpath
                    if variant == "baseline":
                        cp = args.baseline_classpath + os.pathsep + cp
                    command = [java, *report["jvm_args"], "-cp", cp,
                               "com.onyx.interactors.index.impl.HnswSaveBenchmark",
                               "production" if variant == "baseline" else variant, storage,
                               str(args.rows), str(args.dimensions), kind, str(args.measured)]
                    print(f"Running {storage}/{kind}, fork {fork + 1}, {variant}", flush=True)
                    run = subprocess.run(command, cwd=root, env=env, capture_output=True, text=True, timeout=900)
                    if run.returncode:
                        raise RuntimeError(f"Benchmark failed ({run.returncode}):\n{run.stdout}\n{run.stderr}")
                    lines = [line for line in run.stdout.splitlines() if line.startswith("HNSW_BENCHMARK\t")]
                    if len(lines) != 1:
                        raise RuntimeError(f"Missing benchmark result:\n{run.stdout}\n{run.stderr}")
                    result = dict(field.split("=", 1) for field in lines[0].split("\t")[1:])
                    for key, value in result.items():
                        if key not in {"cache", "cache_class", "storage", "kind", "graph_sha256", "query_sha256"}:
                            result[key] = float(value) if "." in value or "E" in value else int(value)
                    result.update(variant=variant, fork=fork + 1)
                    if result["cache_capacity"] > 0 and result["cache_entries_after_saves"] > result["cache_capacity"]:
                        raise RuntimeError(f"Cache capacity exceeded: {result}")
                    identity = (result["graph_sha256"], result["query_sha256"], result["node_writes_per_save"])
                    key = (storage, kind)
                    if key in expected and identity != expected[key]:
                        raise RuntimeError(f"Graph/query/write equivalence failed: {result}")
                    expected[key] = identity
                    report["runs"].append(result)
                    args.output.write_text(json.dumps(report, indent=2) + "\n")
                    print(f"  {result['save_ms_per_op']:.3f} ms/save; "
                          f"{result['node_reads_per_save']:.1f} node reads/save; "
                          f"{result['search_8_qps']:.0f} queries/s; "
                          f"{result['cache_hit_8_ops_per_second'] / 1e6:.1f} M cache hits/s (8 threads)", flush=True)
    print("\nstorage kind variant median_ms/save median_8_thread_qps")
    for storage in args.storage:
        for kind in args.kinds:
            for variant in variants:
                runs = [run for run in report["runs"] if (run["storage"], run["kind"], run["variant"]) == (storage, kind, variant)]
                print(storage, kind, variant,
                      f"{statistics.median(run['save_ms_per_op'] for run in runs):.3f}",
                      f"{statistics.median(run['search_8_qps'] for run in runs):.0f}")
    print(f"Raw results: {args.output}")


if __name__ == "__main__":
    main()
