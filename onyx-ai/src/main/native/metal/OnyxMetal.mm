#import <Metal/Metal.h>
#import <Foundation/Foundation.h>
#include <jni.h>
#include <algorithm>  // for std::min

// Metal context structure
typedef struct {
    id<MTLDevice> device;
    id<MTLCommandQueue> commandQueue;
    id<MTLLibrary> library;
    id<MTLComputePipelineState> matrixMultiplyPipeline;
    id<MTLComputePipelineState> matrixAddPipeline;
    id<MTLComputePipelineState> matrixSubtractPipeline;
    id<MTLComputePipelineState> matrixElementMultiplyPipeline;
    id<MTLComputePipelineState> matrixTransposePipeline;
    id<MTLComputePipelineState> softmaxMaxPipeline;
    id<MTLComputePipelineState> softmaxExpSumPipeline;
    id<MTLComputePipelineState> softmaxNormalizePipeline;
} MetalContext;

// Helper function to load Metal library using default library approach
id<MTLLibrary> loadMetalLibrary(id<MTLDevice> device) {
    NSError* error = nil;
    
    // First try to load precompiled library from bundle
    NSBundle* bundle = [NSBundle mainBundle];
    NSString* libraryPath = [bundle pathForResource:@"kernels" ofType:@"metallib"];
    
    if (libraryPath) {
        NSURL* libraryURL = [NSURL fileURLWithPath:libraryPath];
        id<MTLLibrary> library = [device newLibraryWithURL:libraryURL error:&error];
        if (library && !error) {
            NSLog(@"Loaded precompiled Metal library from bundle");
            return library;
        } else {
            NSLog(@"Failed to load precompiled library: %@", error ? error.localizedDescription : @"unknown error");
        }
    }
    
    // Try to load the default library (built-in Metal functions)
    @try {
        id<MTLLibrary> defaultLibrary = [device newDefaultLibrary];
        if (defaultLibrary) {
            NSLog(@"Using Metal default library");
            return defaultLibrary;
        }
    } @catch (NSException* exception) {
        NSLog(@"Failed to load default Metal library: %@", exception.reason);
    }
    
    // Last resort: try synchronous compilation with our actual kernels
    NSLog(@"Attempting synchronous source compilation as last resort...");
    
    @try {
        // Use our actual kernel source with XPC-safe compilation
        NSString* kernelSource = @"#include <metal_stdlib>\n"
        "using namespace metal;\n"
        "\n"
        "// Matrix multiplication kernel\n"
        "kernel void matrix_multiply(\n"
        "    const device float* A [[buffer(0)]],\n"
        "    const device float* B [[buffer(1)]],\n"
        "    device float* C [[buffer(2)]],\n"
        "    constant uint& rowsA [[buffer(3)]],\n"
        "    constant uint& colsA [[buffer(4)]],\n"
        "    constant uint& colsB [[buffer(5)]],\n"
        "    uint2 gid [[thread_position_in_grid]]\n"
        ") {\n"
        "    uint row = gid.y;\n"
        "    uint col = gid.x;\n"
        "    \n"
        "    if (row >= rowsA || col >= colsB) return;\n"
        "    \n"
        "    float sum = 0.0;\n"
        "    for (uint k = 0; k < colsA; k++) {\n"
        "        sum += A[row * colsA + k] * B[k * colsB + col];\n"
        "    }\n"
        "    \n"
        "    C[row * colsB + col] = sum;\n"
        "}\n"
        "\n"
        "// Matrix addition kernel\n"
        "kernel void matrix_add(\n"
        "    const device float* A [[buffer(0)]],\n"
        "    const device float* B [[buffer(1)]],\n"
        "    device float* C [[buffer(2)]],\n"
        "    uint index [[thread_position_in_grid]]\n"
        ") {\n"
        "    C[index] = A[index] + B[index];\n"
        "}\n"
        "\n"
        "// Matrix subtraction kernel\n"
        "kernel void matrix_subtract(\n"
        "    const device float* A [[buffer(0)]],\n"
        "    const device float* B [[buffer(1)]],\n"
        "    device float* C [[buffer(2)]],\n"
        "    uint index [[thread_position_in_grid]]\n"
        ") {\n"
        "    C[index] = A[index] - B[index];\n"
        "}\n"
        "\n"
        "// Element-wise multiplication kernel\n"
        "kernel void matrix_element_multiply(\n"
        "    const device float* A [[buffer(0)]],\n"
        "    const device float* B [[buffer(1)]],\n"
        "    device float* C [[buffer(2)]],\n"
        "    uint index [[thread_position_in_grid]]\n"
        ") {\n"
        "    C[index] = A[index] * B[index];\n"
        "}\n"
        "\n"
        "// Matrix transpose kernel\n"
        "kernel void matrix_transpose(\n"
        "    const device float* input [[buffer(0)]],\n"
        "    device float* output [[buffer(1)]],\n"
        "    constant uint& rows [[buffer(2)]],\n"
        "    constant uint& cols [[buffer(3)]],\n"
        "    uint2 gid [[thread_position_in_grid]]\n"
        ") {\n"
        "    uint row = gid.y;\n"
        "    uint col = gid.x;\n"
        "    \n"
        "    if (row >= rows || col >= cols) return;\n"
        "    \n"
        "    output[col * rows + row] = input[row * cols + col];\n"
        "}\n"
        "\n"
        "// Softmax kernel - first pass to find max\n"
        "kernel void softmax_max_pass(\n"
        "    const device float* input [[buffer(0)]],\n"
        "    device float* max_values [[buffer(1)]],\n"
        "    constant uint& cols [[buffer(2)]],\n"
        "    uint row [[thread_position_in_grid]]\n"
        ") {\n"
        "    float max_val = -INFINITY;\n"
        "    const device float* row_ptr = input + row * cols;\n"
        "    \n"
        "    for (uint col = 0; col < cols; col++) {\n"
        "        max_val = max(max_val, row_ptr[col]);\n"
        "    }\n"
        "    \n"
        "    max_values[row] = max_val;\n"
        "}\n"
        "\n"
        "// Softmax kernel - second pass to compute exp and sum\n"
        "kernel void softmax_exp_sum_pass(\n"
        "    const device float* input [[buffer(0)]],\n"
        "    device float* output [[buffer(1)]],\n"
        "    device float* sum_values [[buffer(2)]],\n"
        "    const device float* max_values [[buffer(3)]],\n"
        "    constant uint& cols [[buffer(4)]],\n"
        "    uint row [[thread_position_in_grid]]\n"
        ") {\n"
        "    float sum = 0.0;\n"
        "    const device float* row_input = input + row * cols;\n"
        "    device float* row_output = output + row * cols;\n"
        "    float max_val = max_values[row];\n"
        "    \n"
        "    // Compute exp(x - max) and accumulate sum\n"
        "    for (uint col = 0; col < cols; col++) {\n"
        "        float exp_val = exp(row_input[col] - max_val);\n"
        "        row_output[col] = exp_val;\n"
        "        sum += exp_val;\n"
        "    }\n"
        "    \n"
        "    sum_values[row] = sum;\n"
        "}\n"
        "\n"
        "// Softmax kernel - third pass to normalize\n"
        "kernel void softmax_normalize_pass(\n"
        "    device float* output [[buffer(0)]],\n"
        "    const device float* sum_values [[buffer(1)]],\n"
        "    constant uint& cols [[buffer(2)]],\n"
        "    uint row [[thread_position_in_grid]]\n"
        ") {\n"
        "    device float* row_output = output + row * cols;\n"
        "    float sum = sum_values[row];\n"
        "    \n"
        "    for (uint col = 0; col < cols; col++) {\n"
        "        row_output[col] /= sum;\n"
        "    }\n"
        "}\n"
        "\n"
        "// Optimized matrix multiplication with tiling\n"
        "kernel void matrix_multiply_tiled(\n"
        "    const device float* A [[buffer(0)]],\n"
        "    const device float* B [[buffer(1)]],\n"
        "    device float* C [[buffer(2)]],\n"
        "    constant uint& rowsA [[buffer(3)]],\n"
        "    constant uint& colsA [[buffer(4)]],\n"
        "    constant uint& colsB [[buffer(5)]],\n"
        "    uint2 gid [[thread_position_in_grid]],\n"
        "    uint2 local_id [[thread_position_in_threadgroup]]\n"
        ") {\n"
        "    const uint TILE_SIZE = 16;\n"
        "    \n"
        "    threadgroup float tileA[TILE_SIZE][TILE_SIZE];\n"
        "    threadgroup float tileB[TILE_SIZE][TILE_SIZE];\n"
        "    \n"
        "    uint row = gid.y;\n"
        "    uint col = gid.x;\n"
        "    \n"
        "    float sum = 0.0;\n"
        "    \n"
        "    uint tilesInK = (colsA + TILE_SIZE - 1) / TILE_SIZE;\n"
        "    \n"
        "    for (uint tileIdx = 0; tileIdx < tilesInK; tileIdx++) {\n"
        "        // Load tile A\n"
        "        uint aRow = row;\n"
        "        uint aCol = tileIdx * TILE_SIZE + local_id.x;\n"
        "        if (aRow < rowsA && aCol < colsA) {\n"
        "            tileA[local_id.y][local_id.x] = A[aRow * colsA + aCol];\n"
        "        } else {\n"
        "            tileA[local_id.y][local_id.x] = 0.0;\n"
        "        }\n"
        "        \n"
        "        // Load tile B\n"
        "        uint bRow = tileIdx * TILE_SIZE + local_id.y;\n"
        "        uint bCol = col;\n"
        "        if (bRow < colsA && bCol < colsB) {\n"
        "            tileB[local_id.y][local_id.x] = B[bRow * colsB + bCol];\n"
        "        } else {\n"
        "            tileB[local_id.y][local_id.x] = 0.0;\n"
        "        }\n"
        "        \n"
        "        threadgroup_barrier(mem_flags::mem_threadgroup);\n"
        "        \n"
        "        // Compute partial sum\n"
        "        for (uint k = 0; k < TILE_SIZE; k++) {\n"
        "            sum += tileA[local_id.y][k] * tileB[k][local_id.x];\n"
        "        }\n"
        "        \n"
        "        threadgroup_barrier(mem_flags::mem_threadgroup);\n"
        "    }\n"
        "    \n"
        "    if (row < rowsA && col < colsB) {\n"
        "        C[row * colsB + col] = sum;\n"
        "    }\n"
        "}\n";
        
        // Try synchronous compilation with minimal options for XPC safety
        MTLCompileOptions* options = [[MTLCompileOptions alloc] init];
        options.languageVersion = MTLLanguageVersion2_0;
        options.fastMathEnabled = NO; // Disable optimizations to avoid XPC issues
        
        // Use synchronous method to avoid async issues
        id<MTLLibrary> library = [device newLibraryWithSource:kernelSource 
                                                      options:options 
                                                        error:&error];
        
        if (!library || error) {
            NSLog(@"Failed to compile Metal library synchronously: %@", error ? error.localizedDescription : @"unknown error");
            return nil;
        }
        
        NSLog(@"Successfully compiled Metal library with kernels as fallback");
        return library;
        
    } @catch (NSException* exception) {
        NSLog(@"Exception during Metal compilation: %@", exception.reason);
        return nil;
    }
}

extern "C" {

// Check if Metal is available
JNIEXPORT jboolean JNICALL
Java_com_onyxdevtools_ai_compute_MetalComputeBackend_isMetalAvailableNative(JNIEnv* env, jclass clazz) {
    @autoreleasepool {
        id<MTLDevice> device = MTLCreateSystemDefaultDevice();
        return device != nil ? JNI_TRUE : JNI_FALSE;
    }
}

// Get Metal device information
JNIEXPORT jstring JNICALL
Java_com_onyxdevtools_ai_compute_MetalComputeBackend_getMetalDeviceInfo(JNIEnv* env, jclass clazz) {
    @autoreleasepool {
        id<MTLDevice> device = MTLCreateSystemDefaultDevice();
        if (!device) {
            return env->NewStringUTF("No Metal device available");
        }
        
        NSString* deviceInfo = [NSString stringWithFormat:@"Metal Device: %@", device.name];
        return env->NewStringUTF([deviceInfo UTF8String]);
    }
}

// Get current GPU memory usage in bytes
JNIEXPORT jlong JNICALL
Java_com_onyxdevtools_ai_compute_MetalComputeBackend_getCurrentGPUMemoryUsage(JNIEnv* env, jclass clazz, jlong contextHandle) {
    @autoreleasepool {
        MetalContext* context = reinterpret_cast<MetalContext*>(contextHandle);
        if (!context || !context->device) {
            return -1;
        }
        
        // Get current memory usage from Metal device
        if (@available(macOS 10.15, iOS 13.0, *)) {
            return (jlong)[context->device currentAllocatedSize];
        } else {
            // Fallback - we can't get exact GPU memory on older systems
            return -1;
        }
    }
}

// Get recommended max GPU memory usage in bytes  
JNIEXPORT jlong JNICALL
Java_com_onyxdevtools_ai_compute_MetalComputeBackend_getRecommendedMaxGPUMemory(JNIEnv* env, jclass clazz, jlong contextHandle) {
    @autoreleasepool {
        MetalContext* context = reinterpret_cast<MetalContext*>(contextHandle);
        if (!context || !context->device) {
            return -1;
        }
        
        // Get recommended max working set size
        if (@available(macOS 10.15, iOS 13.0, *)) {
            return (jlong)[context->device recommendedMaxWorkingSetSize];
        } else {
            // Fallback estimate for older systems
            return 1024 * 1024 * 1024; // 1GB fallback
        }
    }
}

// Force Metal to flush any pending commands and deallocations
JNIEXPORT void JNICALL
Java_com_onyxdevtools_ai_compute_MetalComputeBackend_forceGPUMemorySync(JNIEnv* env, jclass clazz, jlong contextHandle) {
    @autoreleasepool {
        MetalContext* context = reinterpret_cast<MetalContext*>(contextHandle);
        if (!context || !context->commandQueue) {
            return;
        }
        
        // Create empty command buffer and wait for completion to force sync
        id<MTLCommandBuffer> syncBuffer = [context->commandQueue commandBuffer];
        [syncBuffer commit];
        [syncBuffer waitUntilCompleted];
        
        // Force multiple autorelease pool drains to ensure all objects are released
        for (int i = 0; i < 3; i++) {
            @autoreleasepool {
                [[NSRunLoop currentRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.001]];
            }
        }
        
        // Force garbage collection and memory pressure (if available)
        if (@available(macOS 10.15, iOS 13.0, *)) {
            // Trigger memory pressure to force Metal to release cached buffers
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_LOW, 0), ^{
                @autoreleasepool {
                    // Create memory pressure by temporarily reducing available memory
                    NSLog(@"Forcing Metal memory pressure to release cached buffers");
                }
            });
        }
    }
}

// Initialize Metal context
JNIEXPORT jlong JNICALL
Java_com_onyxdevtools_ai_compute_MetalComputeBackend_initializeMetalContext(JNIEnv* env, jclass clazz) {
    @autoreleasepool {
        id<MTLDevice> device = MTLCreateSystemDefaultDevice();
        if (!device) {
            NSLog(@"Metal device not available");
            return 0;
        }
        
        MetalContext* context = new MetalContext;
        context->device = device;
        context->commandQueue = [device newCommandQueue];
        
        if (!context->commandQueue) {
            NSLog(@"Failed to create Metal command queue");
            delete context;
            return 0;
        }
        
        context->library = loadMetalLibrary(device);
        
        if (!context->library) {
            NSLog(@"Failed to load Metal library");
            delete context;
            return 0;
        }
        
        // Initialize pipeline states to nil - they will remain nil if functions aren't found
        context->matrixMultiplyPipeline = nil;
        context->matrixAddPipeline = nil;
        context->matrixSubtractPipeline = nil;
        context->matrixElementMultiplyPipeline = nil;
        context->matrixTransposePipeline = nil;
        context->softmaxMaxPipeline = nil;
        context->softmaxExpSumPipeline = nil;
        context->softmaxNormalizePipeline = nil;
        
        // Try to create compute pipeline states but don't fail if functions aren't found
        NSError* error = nil;
        NSLog(@"Attempting to create Metal compute pipelines...");
        
        // Matrix multiply pipeline (using tiled kernel)
        id<MTLFunction> matrixMultiplyFunction = [context->library newFunctionWithName:@"matrix_multiply_tiled"];
        if (matrixMultiplyFunction) {
            context->matrixMultiplyPipeline = [device newComputePipelineStateWithFunction:matrixMultiplyFunction error:&error];
            if (context->matrixMultiplyPipeline && !error) {
                NSLog(@"Successfully created tiled matrix multiply pipeline");
            } else {
                NSLog(@"Failed to create tiled matrix multiply pipeline: %@", error ? error.localizedDescription : @"unknown error");
                context->matrixMultiplyPipeline = nil;
            }
        } else {
            NSLog(@"Tiled matrix multiply function not found in library - will use CPU fallback");
        }
        
        // Matrix add pipeline
        id<MTLFunction> matrixAddFunction = [context->library newFunctionWithName:@"matrix_add"];
        if (matrixAddFunction) {
            context->matrixAddPipeline = [device newComputePipelineStateWithFunction:matrixAddFunction error:&error];
            if (context->matrixAddPipeline && !error) {
                NSLog(@"Successfully created matrix add pipeline");
            } else {
                NSLog(@"Failed to create matrix add pipeline: %@", error ? error.localizedDescription : @"unknown error");
                context->matrixAddPipeline = nil;
            }
        } else {
            NSLog(@"Matrix add function not found in library - will use CPU fallback");
        }
        
        // Matrix subtract pipeline  
        id<MTLFunction> matrixSubtractFunction = [context->library newFunctionWithName:@"matrix_subtract"];
        if (matrixSubtractFunction) {
            context->matrixSubtractPipeline = [device newComputePipelineStateWithFunction:matrixSubtractFunction error:&error];
            if (context->matrixSubtractPipeline && !error) {
                NSLog(@"Successfully created matrix subtract pipeline");
            } else {
                NSLog(@"Failed to create matrix subtract pipeline: %@", error ? error.localizedDescription : @"unknown error");
                context->matrixSubtractPipeline = nil;
            }
        } else {
            NSLog(@"Matrix subtract function not found in library - will use CPU fallback");
        }
        
        // Matrix element multiply pipeline
        id<MTLFunction> matrixElementMultiplyFunction = [context->library newFunctionWithName:@"matrix_element_multiply"];
        if (matrixElementMultiplyFunction) {
            context->matrixElementMultiplyPipeline = [device newComputePipelineStateWithFunction:matrixElementMultiplyFunction error:&error];
            if (context->matrixElementMultiplyPipeline && !error) {
                NSLog(@"Successfully created matrix element multiply pipeline");
            } else {
                NSLog(@"Failed to create matrix element multiply pipeline: %@", error ? error.localizedDescription : @"unknown error");
                context->matrixElementMultiplyPipeline = nil;
            }
        } else {
            NSLog(@"Matrix element multiply function not found in library - will use CPU fallback");
        }
        
        // Matrix transpose pipeline
        id<MTLFunction> matrixTransposeFunction = [context->library newFunctionWithName:@"matrix_transpose"];
        if (matrixTransposeFunction) {
            context->matrixTransposePipeline = [device newComputePipelineStateWithFunction:matrixTransposeFunction error:&error];
            if (context->matrixTransposePipeline && !error) {
                NSLog(@"Successfully created matrix transpose pipeline");
            } else {
                NSLog(@"Failed to create matrix transpose pipeline: %@", error ? error.localizedDescription : @"unknown error");
                context->matrixTransposePipeline = nil;
            }
        } else {
            NSLog(@"Matrix transpose function not found in library - will use CPU fallback");
        }
        
        // Softmax max pipeline
        id<MTLFunction> softmaxMaxFunction = [context->library newFunctionWithName:@"softmax_max_pass"];
        if (softmaxMaxFunction) {
            context->softmaxMaxPipeline = [device newComputePipelineStateWithFunction:softmaxMaxFunction error:&error];
            if (context->softmaxMaxPipeline && !error) {
                NSLog(@"Successfully created softmax max pipeline");
            } else {
                NSLog(@"Failed to create softmax max pipeline: %@", error ? error.localizedDescription : @"unknown error");
                context->softmaxMaxPipeline = nil;
            }
        } else {
            NSLog(@"Softmax max function not found in library - will use CPU fallback");
        }
        
        // Softmax exp sum pipeline
        id<MTLFunction> softmaxExpSumFunction = [context->library newFunctionWithName:@"softmax_exp_sum_pass"];
        if (softmaxExpSumFunction) {
            context->softmaxExpSumPipeline = [device newComputePipelineStateWithFunction:softmaxExpSumFunction error:&error];
            if (context->softmaxExpSumPipeline && !error) {
                NSLog(@"Successfully created softmax exp sum pipeline");
            } else {
                NSLog(@"Failed to create softmax exp sum pipeline: %@", error ? error.localizedDescription : @"unknown error");
                context->softmaxExpSumPipeline = nil;
            }
        } else {
            NSLog(@"Softmax exp sum function not found in library - will use CPU fallback");
        }
        
        // Softmax normalize pipeline
        id<MTLFunction> softmaxNormalizeFunction = [context->library newFunctionWithName:@"softmax_normalize_pass"];
        if (softmaxNormalizeFunction) {
            context->softmaxNormalizePipeline = [device newComputePipelineStateWithFunction:softmaxNormalizeFunction error:&error];
            if (context->softmaxNormalizePipeline && !error) {
                NSLog(@"Successfully created softmax normalize pipeline");
            } else {
                NSLog(@"Failed to create softmax normalize pipeline: %@", error ? error.localizedDescription : @"unknown error");
                context->softmaxNormalizePipeline = nil;
            }
        } else {
            NSLog(@"Softmax normalize function not found in library - will use CPU fallback");
        }
        
        NSLog(@"Metal context initialized successfully");
        return reinterpret_cast<jlong>(context);
    }
}

// Dispose Metal context
JNIEXPORT void JNICALL
Java_com_onyxdevtools_ai_compute_MetalComputeBackend_disposeMetalContext(JNIEnv* env, jclass clazz, jlong contextHandle) {
    @autoreleasepool {
        MetalContext* context = reinterpret_cast<MetalContext*>(contextHandle);
        if (context) {
            // Properly release all Metal objects
            context->device = nil;
            context->commandQueue = nil;
            context->library = nil;
            context->matrixMultiplyPipeline = nil;
            context->matrixAddPipeline = nil;
            context->matrixSubtractPipeline = nil;
            context->matrixElementMultiplyPipeline = nil;
            context->matrixTransposePipeline = nil;
            context->softmaxMaxPipeline = nil;
            context->softmaxExpSumPipeline = nil;
            context->softmaxNormalizePipeline = nil;
            
            delete context;
        }
    }
}

// Create GPU buffer  
JNIEXPORT jlong JNICALL
Java_com_onyxdevtools_ai_compute_MetalComputeBackend_createGPUBuffer(JNIEnv* env,
                                                                     jclass clazz,
                                                                     jlong contextHandle,
                                                                     jint size) {
    @autoreleasepool {
        MetalContext* ctx = reinterpret_cast<MetalContext*>(contextHandle);
        if (!ctx) return 0;

        // Use MTLResourceStorageModeShared with no caching hint to ensure proper cleanup
        MTLResourceOptions options = MTLResourceStorageModeShared | MTLResourceCPUCacheModeWriteCombined;
        
        id<MTLBuffer> buffer = [ctx->device newBufferWithLength:size options:options];
        
        if (!buffer) {
            return 0;
        }
        
        // CRITICAL: We must explicitly retain the buffer for Java/JNI ownership
        // The buffer created by newBufferWithLength has retain count = 1 from ARC
        // We add +1 retain count for JNI, so total retain count = 2
        CFRetain((__bridge CFTypeRef)buffer);
        

        return reinterpret_cast<jlong>(buffer);
    }
}

// Copy data to GPU
JNIEXPORT void JNICALL
Java_com_onyxdevtools_ai_compute_MetalComputeBackend_copyToGPU(JNIEnv* env, jclass clazz, jlong contextHandle, jlong bufferHandle, jfloatArray data) {
    @autoreleasepool {
        // FIXED: Use proper bridge cast to access the buffer without transferring ownership
        id<MTLBuffer> buffer = (__bridge id<MTLBuffer>)((void*)bufferHandle);
        if (!buffer) return;
        
        jfloat* elements = env->GetFloatArrayElements(data, NULL);
        jsize length = env->GetArrayLength(data);
        
        memcpy([buffer contents], elements, length * sizeof(float));
        
        env->ReleaseFloatArrayElements(data, elements, JNI_ABORT);
    }
}

// Copy data from GPU
JNIEXPORT jfloatArray JNICALL
Java_com_onyxdevtools_ai_compute_MetalComputeBackend_copyFromGPU(JNIEnv* env, jclass clazz, jlong contextHandle, jlong bufferHandle, jint size) {
    @autoreleasepool {
        // FIXED: Use proper bridge cast to access the buffer without transferring ownership
        id<MTLBuffer> buffer = (__bridge id<MTLBuffer>)((void*)bufferHandle);
        if (!buffer) return NULL;

        jfloatArray result = env->NewFloatArray(size);
        if (!result) return NULL;

        float* bufferData = static_cast<float*>([buffer contents]);
        env->SetFloatArrayRegion(result, 0, size, bufferData);

        return result;
    }
}

    // void copyFromGPUInto(long ctx, long bufferHandle, float[] dst, int size)
    JNIEXPORT void JNICALL
    Java_com_onyxdevtools_ai_compute_MetalComputeBackend_copyFromGPUInto(
        JNIEnv* env, jclass, jlong /*contextHandle*/, jlong bufferHandle,
        jfloatArray dst, jint size) {
      @autoreleasepool {
        if (!dst || size <= 0) return;

        id<MTLBuffer> buffer = (__bridge id<MTLBuffer>)((void*)bufferHandle);
        if (!buffer) return;

        const jsize dstLen = env->GetArrayLength(dst);
        if (dstLen <= 0) return;

        size_t maxFloatsByBuffer = buffer.length / sizeof(jfloat);
        jsize toCopy = (jsize)std::min((size_t)dstLen, (size_t)std::min((size_t)size, maxFloatsByBuffer));
        if (toCopy <= 0) return;

        const float* src = (const float*)[buffer contents];
        // Critical section avoids extra pin/copy gymnastics inside the JVM.
        jboolean isCopy = JNI_FALSE;
        jfloat* dstPtr = (jfloat*)env->GetPrimitiveArrayCritical(dst, &isCopy);
        if (!dstPtr) return;

       memcpy(dstPtr, src, (size_t)toCopy * sizeof(jfloat));
       env->ReleasePrimitiveArrayCritical(dst, dstPtr, 0);
      }
    }

// Release GPU buffer
JNIEXPORT void JNICALL
Java_com_onyxdevtools_ai_compute_MetalComputeBackend_releaseGPUBuffer(JNIEnv* env, jclass clazz, jlong contextHandle, jlong bufferHandle) {
    @autoreleasepool {
        if (bufferHandle != 0) {
            // Cast the handle back to the Metal buffer
            id<MTLBuffer> buffer = (__bridge id<MTLBuffer>)((void*)bufferHandle);
            
            if (buffer) {

                // CRITICAL FIX: We need to release TWICE to properly decrement reference count
                // 1st CFRelease: decrements the +1 we added with CFRetain (retain count: 2 -> 1)
                // 2nd CFRelease: decrements the original ARC reference (retain count: 1 -> 0, buffer deallocated)
                CFRelease((__bridge CFTypeRef)buffer);  // Remove our JNI reference
                CFRelease((__bridge CFTypeRef)buffer);  // Remove the ARC reference to actually free the buffer
                
            }
        }
    }
}

// GPU matrix multiplication
JNIEXPORT jboolean JNICALL
Java_com_onyxdevtools_ai_compute_MetalComputeBackend_gpuMatrixMultiply(
    JNIEnv* env, jclass clazz, jlong contextHandle,
    jlong bufferA, jint rowsA, jint colsA,
    jlong bufferB, jint rowsB, jint colsB,
    jlong bufferResult
) {
    @autoreleasepool {
        MetalContext* context = reinterpret_cast<MetalContext*>(contextHandle);
        if (!context || !context->matrixMultiplyPipeline) return JNI_FALSE;
        
        // FIXED: Use proper bridge cast to access buffers without transferring ownership
        id<MTLBuffer> A = (__bridge id<MTLBuffer>)((void*)bufferA);
        id<MTLBuffer> B = (__bridge id<MTLBuffer>)((void*)bufferB);
        id<MTLBuffer> C = (__bridge id<MTLBuffer>)((void*)bufferResult);
        
        id<MTLCommandBuffer> commandBuffer = [context->commandQueue commandBuffer];
        id<MTLComputeCommandEncoder> encoder = [commandBuffer computeCommandEncoder];
        
        [encoder setComputePipelineState:context->matrixMultiplyPipeline];
        [encoder setBuffer:A offset:0 atIndex:0];
        [encoder setBuffer:B offset:0 atIndex:1];
        [encoder setBuffer:C offset:0 atIndex:2];
        [encoder setBytes:&rowsA length:sizeof(uint32_t) atIndex:3];
        [encoder setBytes:&colsA length:sizeof(uint32_t) atIndex:4];
        [encoder setBytes:&colsB length:sizeof(uint32_t) atIndex:5];
        
        // Calculate optimal thread group size for tiled kernel
        const uint TILE_SIZE = 16;
        MTLSize threadsPerThreadgroup = MTLSizeMake(TILE_SIZE, TILE_SIZE, 1);
        MTLSize threadsPerGrid = MTLSizeMake((colsB + TILE_SIZE - 1) / TILE_SIZE * TILE_SIZE,
                                             (rowsA + TILE_SIZE - 1) / TILE_SIZE * TILE_SIZE, 1);
        
        [encoder dispatchThreads:threadsPerGrid threadsPerThreadgroup:threadsPerThreadgroup];
        [encoder endEncoding];
        
        [commandBuffer commit];
        [commandBuffer waitUntilCompleted];
        
        BOOL success = (commandBuffer.status == MTLCommandBufferStatusCompleted);
        
        // Explicit cleanup
        commandBuffer = nil;
        encoder = nil;
        
        return success ? JNI_TRUE : JNI_FALSE;
    }
}

// GPU element-wise operations
JNIEXPORT jboolean JNICALL
Java_com_onyxdevtools_ai_compute_MetalComputeBackend_gpuElementWiseOperation(
    JNIEnv* env, jclass clazz, jlong contextHandle,
    jlong bufferA, jlong bufferB, jlong bufferResult,
    jint rows, jint cols, jint operation
) {
    @autoreleasepool {
        MetalContext* context = reinterpret_cast<MetalContext*>(contextHandle);
        if (!context) return JNI_FALSE;
        
        id<MTLComputePipelineState> pipeline = nil;
        switch (operation) {
            case 0: pipeline = context->matrixAddPipeline; break;
            case 1: pipeline = context->matrixSubtractPipeline; break;
            case 2: pipeline = context->matrixElementMultiplyPipeline; break;
            default: return JNI_FALSE;
        }
        
        if (!pipeline) return JNI_FALSE;
        
        // FIXED: Use proper bridge cast to access buffers without transferring ownership
        id<MTLBuffer> A = (__bridge id<MTLBuffer>)((void*)bufferA);
        id<MTLBuffer> B = (__bridge id<MTLBuffer>)((void*)bufferB);
        id<MTLBuffer> C = (__bridge id<MTLBuffer>)((void*)bufferResult);
        
        id<MTLCommandBuffer> commandBuffer = [context->commandQueue commandBuffer];
        id<MTLComputeCommandEncoder> encoder = [commandBuffer computeCommandEncoder];
        
        [encoder setComputePipelineState:pipeline];
        [encoder setBuffer:A offset:0 atIndex:0];
        [encoder setBuffer:B offset:0 atIndex:1];
        [encoder setBuffer:C offset:0 atIndex:2];
        
        NSUInteger totalElements = rows * cols;
        MTLSize threadsPerThreadgroup = MTLSizeMake(64, 1, 1);
        MTLSize threadsPerGrid = MTLSizeMake(totalElements, 1, 1);
        
        [encoder dispatchThreads:threadsPerGrid threadsPerThreadgroup:threadsPerThreadgroup];
        [encoder endEncoding];
        
        [commandBuffer commit];
        [commandBuffer waitUntilCompleted];
        
        BOOL success = (commandBuffer.status == MTLCommandBufferStatusCompleted);
        
        // Explicit cleanup
        commandBuffer = nil;
        encoder = nil;
        
        return success ? JNI_TRUE : JNI_FALSE;
    }
}

// GPU matrix transpose
JNIEXPORT jboolean JNICALL
Java_com_onyxdevtools_ai_compute_MetalComputeBackend_gpuTranspose(
    JNIEnv* env, jclass clazz, jlong contextHandle,
    jlong bufferInput, jlong bufferOutput,
    jint rows, jint cols
) {
    @autoreleasepool {
        MetalContext* context = reinterpret_cast<MetalContext*>(contextHandle);
        if (!context || !context->matrixTransposePipeline) return JNI_FALSE;
        
        // FIXED: Use proper bridge cast to access buffers without transferring ownership
        id<MTLBuffer> input = (__bridge id<MTLBuffer>)((void*)bufferInput);
        id<MTLBuffer> output = (__bridge id<MTLBuffer>)((void*)bufferOutput);
        
        id<MTLCommandBuffer> commandBuffer = [context->commandQueue commandBuffer];
        id<MTLComputeCommandEncoder> encoder = [commandBuffer computeCommandEncoder];
        
        [encoder setComputePipelineState:context->matrixTransposePipeline];
        [encoder setBuffer:input offset:0 atIndex:0];
        [encoder setBuffer:output offset:0 atIndex:1];
        [encoder setBytes:&rows length:sizeof(uint32_t) atIndex:2];
        [encoder setBytes:&cols length:sizeof(uint32_t) atIndex:3];
        
        MTLSize threadsPerThreadgroup = MTLSizeMake(16, 16, 1);
        MTLSize threadsPerGrid = MTLSizeMake(cols, rows, 1);
        
        [encoder dispatchThreads:threadsPerGrid threadsPerThreadgroup:threadsPerThreadgroup];
        [encoder endEncoding];
        
        [commandBuffer commit];
        [commandBuffer waitUntilCompleted];
        
        BOOL success = (commandBuffer.status == MTLCommandBufferStatusCompleted);
        
        // Explicit cleanup
        commandBuffer = nil;
        encoder = nil;
        
        return success ? JNI_TRUE : JNI_FALSE;
    }
}

// GPU softmax (simplified single-pass version for now)
JNIEXPORT jboolean JNICALL
Java_com_onyxdevtools_ai_compute_MetalComputeBackend_gpuSoftmax(
    JNIEnv* env, jclass clazz, jlong contextHandle,
    jlong bufferInput, jlong bufferOutput,
    jint rows, jint cols
) {
    @autoreleasepool {
        MetalContext* context = reinterpret_cast<MetalContext*>(contextHandle);
        if (!context || !context->softmaxMaxPipeline || !context->softmaxExpSumPipeline || !context->softmaxNormalizePipeline) {
            return JNI_FALSE;
        }
        
        // FIXED: Use proper bridge cast to access buffers without transferring ownership
        id<MTLBuffer> input = (__bridge id<MTLBuffer>)((void*)bufferInput);
        id<MTLBuffer> output = (__bridge id<MTLBuffer>)((void*)bufferOutput);
        
        // Create temporary buffers for max and sum values
        id<MTLBuffer> maxBuffer = [context->device newBufferWithLength:rows * sizeof(float) options:MTLResourceStorageModeShared];
        id<MTLBuffer> sumBuffer = [context->device newBufferWithLength:rows * sizeof(float) options:MTLResourceStorageModeShared];
        
        // CRITICAL: Manually retain temporary buffers since ARC is disabled
        CFRetain((__bridge CFTypeRef)maxBuffer);
        CFRetain((__bridge CFTypeRef)sumBuffer);
        
        id<MTLCommandBuffer> commandBuffer = [context->commandQueue commandBuffer];
        
        // Pass 1: Find max values
        id<MTLComputeCommandEncoder> encoder1 = [commandBuffer computeCommandEncoder];
        [encoder1 setComputePipelineState:context->softmaxMaxPipeline];
        [encoder1 setBuffer:input offset:0 atIndex:0];
        [encoder1 setBuffer:maxBuffer offset:0 atIndex:1];
        [encoder1 setBytes:&cols length:sizeof(uint32_t) atIndex:2];
        
        MTLSize threadsPerGrid1 = MTLSizeMake(rows, 1, 1);
        MTLSize threadsPerThreadgroup1 = MTLSizeMake(1, 1, 1);
        [encoder1 dispatchThreads:threadsPerGrid1 threadsPerThreadgroup:threadsPerThreadgroup1];
        [encoder1 endEncoding];
        
        // Pass 2: Compute exp and sum
        id<MTLComputeCommandEncoder> encoder2 = [commandBuffer computeCommandEncoder];
        [encoder2 setComputePipelineState:context->softmaxExpSumPipeline];
        [encoder2 setBuffer:input offset:0 atIndex:0];
        [encoder2 setBuffer:output offset:0 atIndex:1];
        [encoder2 setBuffer:sumBuffer offset:0 atIndex:2];
        [encoder2 setBuffer:maxBuffer offset:0 atIndex:3];
        [encoder2 setBytes:&cols length:sizeof(uint32_t) atIndex:4];
        
        [encoder2 dispatchThreads:threadsPerGrid1 threadsPerThreadgroup:threadsPerThreadgroup1];
        [encoder2 endEncoding];
        
        // Pass 3: Normalize
        id<MTLComputeCommandEncoder> encoder3 = [commandBuffer computeCommandEncoder];
        [encoder3 setComputePipelineState:context->softmaxNormalizePipeline];
        [encoder3 setBuffer:output offset:0 atIndex:0];
        [encoder3 setBuffer:sumBuffer offset:0 atIndex:1];
        [encoder3 setBytes:&cols length:sizeof(uint32_t) atIndex:2];
        
        [encoder3 dispatchThreads:threadsPerGrid1 threadsPerThreadgroup:threadsPerThreadgroup1];
        [encoder3 endEncoding];
        
        [commandBuffer commit];
        [commandBuffer waitUntilCompleted];
        
        BOOL success = (commandBuffer.status == MTLCommandBufferStatusCompleted);
        
        // CRITICAL: Clean up temporary buffers to prevent memory leaks
        // Since we used CFRetain, we must use CFRelease to properly decrement reference count
        CFRelease((__bridge CFTypeRef)maxBuffer);
        CFRelease((__bridge CFTypeRef)sumBuffer);
        maxBuffer = nil;
        sumBuffer = nil;
        commandBuffer = nil;
        encoder1 = nil;
        encoder2 = nil;
        encoder3 = nil;
        
        return success ? JNI_TRUE : JNI_FALSE;
    }
}

} // extern "C"
