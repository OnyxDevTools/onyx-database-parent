#!/bin/bash

# Build script for Metal GPU backend native library
# This script compiles only the JNI native library - shaders are compiled at runtime

set -e

OUTPUT_DIR=${1:-"build"}
LIBRARY_NAME="libonyx-metal.dylib"

# Ensure output directory exists
mkdir -p "$OUTPUT_DIR"

echo "Building Metal native library..."
echo "Output directory: $OUTPUT_DIR"

# Check if we're on macOS
if [[ "$OSTYPE" != "darwin"* ]]; then
    echo "Warning: Metal framework only available on macOS"
    exit 0
fi

# Check for Xcode command line tools
if ! command -v clang++ &> /dev/null; then
    echo "Error: Xcode command line tools not found. Please run: xcode-select --install"
    exit 1
fi

# Get Java include paths
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home)}
JAVA_INCLUDE="$JAVA_HOME/include"
JAVA_INCLUDE_DARWIN="$JAVA_HOME/include/darwin"

if [[ ! -d "$JAVA_INCLUDE" ]]; then
    echo "Error: Java include directory not found at $JAVA_INCLUDE"
    echo "Found JAVA_HOME: $JAVA_HOME"
    exit 1
fi

echo "Using JAVA_HOME: $JAVA_HOME"

# Compile flags
CFLAGS="-Wall -Wextra -O3 -fPIC -shared"
INCLUDES="-I$JAVA_INCLUDE -I$JAVA_INCLUDE_DARWIN"
FRAMEWORKS="-framework Metal -framework Foundation -framework CoreGraphics"
LIBS="-lobjc"

# Compile the native library
echo "Compiling native library..."
clang++ $CFLAGS $INCLUDES $FRAMEWORKS $LIBS \
    -o "$OUTPUT_DIR/$LIBRARY_NAME" \
    OnyxMetal.mm

if [[ $? -eq 0 ]]; then
    echo "Successfully built $LIBRARY_NAME"
    ls -la "$OUTPUT_DIR/$LIBRARY_NAME"
    
    # Set proper permissions
    chmod 755 "$OUTPUT_DIR/$LIBRARY_NAME"
    
    echo "Build completed successfully!"
    echo "Output files:"
    echo "  - $OUTPUT_DIR/$LIBRARY_NAME"
else
    echo "Failed to build native library"
    exit 1
fi

echo "Metal native library build complete!"
echo "Note: Metal shaders will be compiled at runtime for better compatibility"
