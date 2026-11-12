#!/usr/bin/env python3
"""Run the native llama.cpp JSON-prefill regression without loading a model."""
import argparse
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--source", required=True, type=Path)
parser.add_argument("--build-dir", required=True, type=Path)
parser.add_argument("--cmake", default="cmake")
args = parser.parse_args()
source = args.source.resolve()
build = args.build_dir.resolve()
subprocess.run([
    args.cmake, "-S", str(source), "-B", str(build),
    "-DLLAMA_BUILD_COMMON=ON", "-DLLAMA_BUILD_TESTS=OFF",
    "-DLLAMA_BUILD_TOOLS=OFF", "-DLLAMA_BUILD_EXAMPLES=OFF",
    "-DLLAMA_BUILD_APP=OFF", "-DLLAMA_BUILD_SERVER=OFF",
    "-DLLAMA_CURL=OFF", "-DGGML_NATIVE=OFF", "-DGGML_OPENMP=OFF",
    "-DGGML_CPU_KLEIDIAI=OFF", "-DCMAKE_BUILD_TYPE=Release",
], check=True)
subprocess.run([args.cmake, "--build", str(build), "--target", "llama-common", "-j", "2"], check=True)
binary = build / "verify-structured-sampler"
command = [os.environ.get("CXX", "c++"), "-std=c++17",
           str(Path(__file__).with_suffix(".cpp"))]
# The pinned npm vendor omits this source from common/parsers/sources.cmake.
command.append(str(source / "common/parsers/function-gemma.cpp"))
for directory in ("common", "src", "include", "ggml/include"):
    command.append("-I" + str(source / directory))
command += ["-L" + str(build / "bin"), "-Wl,-rpath," + str(build / "bin"),
            "-lllama-common", "-lllama", "-lggml", "-lggml-base", "-o", str(binary)]
subprocess.run(command, check=True)
subprocess.run([str(binary)], check=True)
