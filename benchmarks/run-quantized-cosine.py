#!/usr/bin/env python3
"""Compare quantized cosine implementations in serial, alternating JDK 23 JVM forks."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--forks", type=int, default=3)
    parser.add_argument("--dimensions", type=int, nargs="+", default=[688, 1536])
    parser.add_argument("--baseline-classpath", help="Pre-change main classes, prepended in baseline forks")
    parser.add_argument("--output", type=Path, default=Path("/tmp/onyx-quantized-cosine-benchmark.json"))
    args = parser.parse_args()
    if args.forks < 1 or any(not 1 <= size <= 16_384 for size in args.dimensions):
        parser.error("Use positive forks and dimensions between 1 and 16384")
    if not os.environ.get("JAVA_HOME"):
        parser.error("Set JAVA_HOME to a JDK 23 installation")
    root = Path(__file__).resolve().parents[1]
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java")
    java_version = subprocess.run([java, "-version"], capture_output=True, text=True, check=True).stderr.strip()
    if not re.search(r'\bversion "23(?:[.+-]|")', java_version):
        parser.error(f"JAVA_HOME must select JDK 23; found: {java_version}")
    with tempfile.TemporaryDirectory(prefix="cosine-benchmark-build-") as temporary:
        temporary = Path(temporary)
        init = temporary / "classpath.gradle"
        classpath_file = temporary / "classpath.txt"
        init.write_text('''gradle.projectsEvaluated {
    def database = gradle.rootProject.findProject(":onyx-database")
    if (database == null) return
    database.tasks.register("writeCosineBenchmarkClasspath") {
        dependsOn(database.tasks.named("testClasses"))
        doLast {
            new File(System.getProperty("cosine.benchmark.classpathFile")).text =
                database.sourceSets.test.runtimeClasspath.asPath
        }
    }
}
''')
        subprocess.run([
            str(root / "gradlew"), "--console=plain", "-I", str(init),
            f"-Dcosine.benchmark.classpathFile={classpath_file}",
            ":onyx-database:writeCosineBenchmarkClasspath",
        ], cwd=root, check=True)
        classpath = classpath_file.read_text().strip()
    source_directory = root / "onyx-database/src/main/kotlin/com/onyx/vector"
    report = {
        "java": java_version,
        "jvm_args": ["-Xms512m", "-Xmx1g", "-XX:ActiveProcessorCount=4"],
        "simd_jvm_args": ["--add-modules=jdk.incubator.vector"],
        "source_sha256": {name: hashlib.sha256((source_directory / name).read_bytes()).hexdigest()
                          for name in ["QuantizedCosineVector.kt", "VectorizedByteDotProduct.kt"]},
        "parameters": {key: str(value) if isinstance(value, Path) else value for key, value in vars(args).items()},
        "runs": [],
    }
    variants = (["baseline"] if args.baseline_classpath else []) + ["scalar", "simd"]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    expected = {}
    for dimensions in args.dimensions:
        for fork in range(args.forks):
            order = variants[fork % len(variants):] + variants[:fork % len(variants)]
            for variant in order:
                cp = args.baseline_classpath + os.pathsep + classpath if variant == "baseline" else classpath
                flags = report["simd_jvm_args"] if variant == "simd" else []
                command = [java, *report["jvm_args"], *flags, "-cp", cp,
                           "com.onyx.vector.QuantizedCosineBenchmark", str(dimensions)]
                print(f"Running {dimensions} dimensions, fork {fork + 1}, {variant}", flush=True)
                run = subprocess.run(command, cwd=root, capture_output=True, text=True, timeout=120)
                if run.returncode:
                    raise RuntimeError(f"Benchmark failed ({run.returncode}):\n{run.stdout}\n{run.stderr}")
                lines = [line for line in run.stdout.splitlines() if line.startswith("COSINE_BENCHMARK\t")]
                if len(lines) != 1:
                    raise RuntimeError(f"Missing benchmark result:\n{run.stdout}\n{run.stderr}")
                result = dict(field.split("=", 1) for field in lines[0].split("\t")[1:])
                result = {key: float(value) if any(c in value.lower() for c in ".e") else int(value)
                          for key, value in result.items()}
                result.update(variant=variant, fork=fork + 1)
                identity = (result["checksum"], result["retained_checksum"])
                if result["dimensions"] != dimensions or identity != expected.setdefault(dimensions, identity):
                    raise RuntimeError(f"Score/construction equivalence failed: {result}")
                report["runs"].append(result)
                args.output.write_text(json.dumps(report, indent=2) + "\n")
                print(f"  cosine {result['cosine_ns_per_op']:.1f} ns/op, "
                      f"{result['cosine_allocated_bytes_per_op']:.1f} B/op; "
                      f"fromBytes {result['from_bytes_ns_per_op']:.1f} ns/op, "
                      f"{result['from_bytes_allocated_bytes_per_op']:.1f} B/op", flush=True)
    print("\ndimensions variant median_cosine_ns/op cosine_speedup median_fromBytes_ns/op fromBytes_speedup")
    for dimensions in args.dimensions:
        medians = {}
        for variant in variants:
            runs = [run for run in report["runs"] if (run["dimensions"], run["variant"]) == (dimensions, variant)]
            medians[variant] = tuple(statistics.median(run[key] for run in runs)
                                     for key in ["cosine_ns_per_op", "from_bytes_ns_per_op"])
        reference = medians[variants[0]]
        for variant, (cosine, construction) in medians.items():
            print(dimensions, variant, f"{cosine:.1f}", f"{reference[0] / cosine:.2f}x",
                  f"{construction:.1f}", f"{reference[1] / construction:.2f}x")
    print(f"Speedups relative to {variants[0]}; raw results: {args.output}")


if __name__ == "__main__":
    main()
