#!/usr/bin/env python3
"""Compare HNSW reconstruction with topology copying in isolated, temporary mapped stores."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rows", type=int, default=5000)
    parser.add_argument("--dimensions", type=int, default=384)
    parser.add_argument("--forks", type=int, default=3)
    parser.add_argument("--output", type=Path, default=Path("/tmp/onyx-hnsw-clone-benchmark.json"))
    args = parser.parse_args()
    if min(args.rows, args.dimensions, args.forks) <= 0:
        parser.error("Rows, dimensions and forks must be positive")
    root = Path(__file__).resolve().parents[1]
    java_home = Path(os.environ["JAVA_HOME"]) if "JAVA_HOME" in os.environ else Path(shutil.which("java")).resolve().parents[1]
    java = str(java_home / "bin/java")
    with tempfile.TemporaryDirectory(prefix="hnsw-clone-build-") as temporary:
        temporary = Path(temporary)
        init = temporary / "classpath.gradle"
        classpath_file = temporary / "classpath.txt"
        init.write_text('''gradle.projectsEvaluated {
    def database = gradle.rootProject.findProject(":onyx-database")
    if (database == null) return
    database.tasks.register("writeHnswCloneClasspath") {
        dependsOn(database.tasks.named("testClasses"))
        doLast {
            new File(System.getProperty("hnsw.clone.classpathFile")).text = database.sourceSets.test.runtimeClasspath.asPath
        }
    }
}
''')
        subprocess.run([str(root / "gradlew"), "--console=plain", "--max-workers=4", "-I", str(init),
                        f"-Dhnsw.clone.classpathFile={classpath_file}", ":onyx-database:writeHnswCloneClasspath"],
                       cwd=root, check=True)
        classpath = classpath_file.read_text().strip()
    jvm_args = ["--add-modules=jdk.incubator.vector", "-Xms512m", "-Xmx2g", "-XX:ActiveProcessorCount=4", "-XX:-UseCompressedOops"]
    source = root / "onyx-database/src/main/kotlin/com/onyx/interactors/index/impl/PersistentHnswIndex.kt"
    report = {"java": subprocess.run([java, "-version"], capture_output=True, text=True, check=True).stderr,
              "jvm_args": jvm_args, "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "runs": []}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    for fork in range(args.forks):
        print(f"Comparing rebuild and copy: rows={args.rows}, dimensions={args.dimensions}, fork={fork + 1}", flush=True)
        run = subprocess.run([java, *jvm_args, "-cp", classpath, "com.onyx.interactors.index.impl.HnswCloneBenchmark",
                              str(args.rows), str(args.dimensions)], cwd=root, capture_output=True, text=True, timeout=900)
        if run.returncode:
            raise RuntimeError(run.stdout + run.stderr)
        line = next(line for line in run.stdout.splitlines() if line.startswith("HNSW_CLONE_BENCHMARK "))
        result = {key: float(value) if "." in value or "E" in value else int(value)
                  for key, value in (field.split("=", 1) for field in line.split()[1:])}
        result["fork"] = fork + 1
        report["runs"].append(result)
        args.output.write_text(json.dumps(report, indent=2) + "\n")
        print(line, flush=True)
    print(f"Raw results: {args.output}")


if __name__ == "__main__":
    main()
