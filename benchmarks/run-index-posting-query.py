#!/usr/bin/env python3
"""Compare native posting traversal and query filtering using existing build classes (JDK 23)."""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-ref", default="c9781938ffd291ad365fbcb0f9f5bc29f9e5db9a",
                        help="Git revision providing the baseline posting map and page")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.environ.get("JAVA_HOME") else shutil.which("java")
    if not java:
        parser.error("Select JDK 23 through JAVA_HOME or PATH")
    classes = root / "onyx-database/build/classes"
    if not (classes / "kotlin/main/com/onyx/diskmap/impl/DiskIndexPostingMap.class").is_file():
        parser.error("Build current classes first: ./gradlew :onyx-database:classes")
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
    kotlin_version = "2.2.10"

    def jar(group, module, version):
        candidates = sorted(path for path in (cache / group / module / version).glob("*/*.jar")
                            if not path.name.endswith(("-sources.jar", "-javadoc.jar")))
        if not candidates:
            parser.error(f"Missing cached {module}:{version} in {cache}; compile the project first")
        return str(candidates[0])

    runtime = [str(classes / "kotlin/main"), str(classes / "java/main"),
               jar("org.jetbrains.kotlin", "kotlin-stdlib", kotlin_version),
               jar("org.jetbrains.kotlin", "kotlin-reflect", kotlin_version)]
    compiler = runtime + [
        jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", kotlin_version),
        jar("org.jetbrains.kotlin", "kotlin-script-runtime", kotlin_version),
        jar("org.jetbrains.kotlin", "kotlin-daemon-embeddable", kotlin_version),
        jar("org.jetbrains.intellij.deps", "trove4j", "1.0.20200330"),
        jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"),
        jar("org.jetbrains", "annotations", "23.0.0"),
    ]
    baseline = subprocess.check_output(
        ["git", "rev-parse", "--verify", "--end-of-options", f"{args.baseline_ref}^{{commit}}"],
        cwd=root, text=True).strip()
    print(f"Baseline: {baseline}; current classes: {classes}", flush=True)
    with tempfile.TemporaryDirectory(prefix="onyx-index-posting-benchmark-") as temporary:
        temporary = Path(temporary)
        sources = [str(root / "benchmarks/IndexPostingQueryFilterBenchmark.kt")]
        for suffix in ["impl/DiskIndexPostingMap.kt", "data/IndexPostingPage.kt"]:
            source = subprocess.check_output(
                ["git", "show", f"{baseline}:onyx-database/src/main/kotlin/com/onyx/diskmap/{suffix}"],
                cwd=root, text=True)
            source = source.replace("DiskIndexPostingMap", "BaselineDiskIndexPostingMap")
            source = source.replace("IndexPostingPage", "BaselineIndexPostingPage")
            path = temporary / ("Baseline" + Path(suffix).name)
            path.write_text(source, encoding="utf-8")
            sources.append(str(path))
        output = temporary / "classes"
        subprocess.run([
            java, "-cp", os.pathsep.join(compiler), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-no-stdlib", "-no-reflect", "-jvm-target", "23", "-classpath", os.pathsep.join(runtime),
            "-d", str(output), *sources,
        ], cwd=root, check=True)
        subprocess.run([
            java, "-Xms256m", "-Xmx768m", "-XX:-UseCompressedOops",
            "-cp", os.pathsep.join([str(output), *runtime]), "QueryFilterBenchmark",
        ], cwd=root, check=True)


if __name__ == "__main__":
    main()
