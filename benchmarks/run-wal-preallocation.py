#!/usr/bin/env python3
"""Compare initial WAL mapping reservations using existing build classes and JDK 23.

Runs serially against disposable real files, with a 128 MiB maximum heap. The only
variant is WholeFileMapping's initial capacity: 4 MiB versus 24 MiB (the 20 MiB
rotation threshold plus one 4 MiB growth quantum for a record crossing it). Each
case writes through the 20 MiB WAL threshold and about 2 MiB in a second WAL,
using 4/16 KiB payloads plus 5-byte headers, checking every byte after
close. A 20 ms force is performed inline when due to include its serialized cost;
this does not model the live flusher's thread scheduling, multiple writers,
serialization, compression, or any full ingestion behavior. Mapping/metadata-force
counts include both files and close. Timing excludes verification and cleanup.
"""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repetitions", type=int, default=3)
    parser.add_argument("--output", type=Path, default=Path("/tmp/onyx-wal-preallocation-benchmark.csv"))
    args = parser.parse_args()
    if args.repetitions < 1:
        parser.error("--repetitions must be positive")
    root = Path(__file__).resolve().parents[1]
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
    javac = str(Path(java_home) / "bin/javac") if java_home else shutil.which("javac")
    if not java or not javac:
        parser.error("Select JDK 23 through JAVA_HOME or PATH")
    classes = root / "onyx-database/build/classes/kotlin/main"
    if not (classes / "com/onyx/diskmap/store/impl/WholeFileMapping.class").is_file():
        parser.error("Build current classes first: ./gradlew :onyx-database:classes")
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
    candidates = sorted((cache / "org.jetbrains.kotlin/kotlin-stdlib/2.2.10").glob("*/*.jar"))
    candidates = [path for path in candidates if not path.name.endswith(("-sources.jar", "-javadoc.jar"))]
    if not candidates:
        parser.error("Missing cached Kotlin 2.2.10 stdlib; compile the project first")
    runtime = os.pathsep.join([str(classes), str(candidates[0])])
    with tempfile.TemporaryDirectory(prefix="onyx-wal-preallocation-classes-") as temporary:
        subprocess.run([javac, "--release", "23", "-cp", runtime, "-d", temporary,
                        str(root / "benchmarks/WalMappingPreallocationBenchmark.java")], check=True)
        result = subprocess.run([java, "-Xms32m", "-Xmx128m", "-XX:ActiveProcessorCount=2",
                                 "-cp", os.pathsep.join([temporary, runtime]),
                                 "WalMappingPreallocationBenchmark", str(args.repetitions)],
                                check=True, text=True, stdout=subprocess.PIPE)
    print(result.stdout, end="")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(result.stdout, encoding="utf-8")
    print(f"Results written to {args.output}")


if __name__ == "__main__":
    main()
