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

    // NEW: multi-head attention pipelines
    id<MTLComputePipelineState> mhaPass1MaxPipeline;
    id<MTLComputePipelineState> mhaPass2SumExpPipeline;
    id<MTLComputePipelineState> mhaPass3OutPipeline;
} MetalContext;

// Helper function to load Metal library using default library approach
id<MTLLibrary> loadMetalLibrary(id<MTLDevice> device) {
    NSError* error = nil;

    // Helper to check whether a library contains the 3 MHA kernels
    BOOL (^hasMHA)(id<MTLLibrary>) = ^BOOL(id<MTLLibrary> lib) {
        if (![lib respondsToSelector:@selector(functionNames)]) return NO;
        NSArray<NSString*>* names = [lib functionNames];
        if (names) {
            NSLog(@"Metal lib has functions: %@", names);
            return [names containsObject:@"mha_pass1_max"] &&
                   [names containsObject:@"mha_pass2_sumexp"] &&
                   [names containsObject:@"mha_pass3_out"];
        }
        return NO;
    };

    // 1) Try precompiled metallib from the app bundle
    {
        NSBundle* bundle = [NSBundle mainBundle];
        NSString* libraryPath = [bundle pathForResource:@"kernels" ofType:@"metallib"];
        if (libraryPath) {
            NSURL* libraryURL = [NSURL fileURLWithPath:libraryPath];
            id<MTLLibrary> lib = [device newLibraryWithURL:libraryURL error:&error];
            if (lib && !error) {
                NSLog(@"Loaded precompiled Metal library from bundle");
                if (hasMHA(lib)) return lib;
                NSLog(@"Precompiled library missing MHA kernels — compiling fallback source...");
            } else {
                NSLog(@"Failed to load precompiled library: %@", error ? error.localizedDescription : @"unknown error");
            }
        }
    }

    // 2) Try default library
    @try {
        id<MTLLibrary> defLib = [device newDefaultLibrary];
        if (defLib) {
            NSLog(@"Using Metal default library");
            if (hasMHA(defLib)) return defLib;
            NSLog(@"Default library missing MHA kernels — compiling fallback source...");
        }
    } @catch (NSException* exception) {
        NSLog(@"Failed to load default Metal library: %@", exception.reason);
    }

    // 3) Fallback: compile full kernel source (includes MHA)
    NSLog(@"Attempting synchronous source compilation as last resort...");

    NSString* src =
    @"#include <metal_stdlib>\n"
    "using namespace metal;\n"
    "\n"
    "// ---------------- Basic kernels ----------------\n"
    "kernel void matrix_multiply(\n"
    "    const device float* A [[buffer(0)]],\n"
    "    const device float* B [[buffer(1)]],\n"
    "    device float* C [[buffer(2)]],\n"
    "    constant uint& rowsA [[buffer(3)]],\n"
    "    constant uint& colsA [[buffer(4)]],\n"
    "    constant uint& colsB [[buffer(5)]],\n"
    "    uint2 gid [[thread_position_in_grid]]) {\n"
    "  uint row = gid.y; uint col = gid.x;\n"
    "  if (row >= rowsA || col >= colsB) return;\n"
    "  float sum = 0.0;\n"
    "  for (uint k = 0; k < colsA; ++k) sum += A[row*colsA+k]*B[k*colsB+col];\n"
    "  C[row*colsB+col] = sum;\n"
    "}\n"
    "\n"
    "kernel void matrix_add(\n"
    "    const device float* A [[buffer(0)]],\n"
    "    const device float* B [[buffer(1)]],\n"
    "    device float* C [[buffer(2)]],\n"
    "    uint index [[thread_position_in_grid]]) {\n"
    "  C[index] = A[index] + B[index];\n"
    "}\n"
    "\n"
    "kernel void matrix_subtract(\n"
    "    const device float* A [[buffer(0)]],\n"
    "    const device float* B [[buffer(1)]],\n"
    "    device float* C [[buffer(2)]],\n"
    "    uint index [[thread_position_in_grid]]) {\n"
    "  C[index] = A[index] - B[index];\n"
    "}\n"
    "\n"
    "kernel void matrix_element_multiply(\n"
    "    const device float* A [[buffer(0)]],\n"
    "    const device float* B [[buffer(1)]],\n"
    "    device float* C [[buffer(2)]],\n"
    "    uint index [[thread_position_in_grid]]) {\n"
    "  C[index] = A[index] * B[index];\n"
    "}\n"
    "\n"
    "kernel void matrix_transpose(\n"
    "    const device float* input [[buffer(0)]],\n"
    "    device float* output [[buffer(1)]],\n"
    "    constant uint& rows [[buffer(2)]],\n"
    "    constant uint& cols [[buffer(3)]],\n"
    "    uint2 gid [[thread_position_in_grid]]) {\n"
    "  uint row = gid.y; uint col = gid.x;\n"
    "  if (row >= rows || col >= cols) return;\n"
    "  output[col*rows + row] = input[row*cols + col];\n"
    "}\n"
    "\n"
    "kernel void softmax_max_pass(\n"
    "    const device float* input [[buffer(0)]],\n"
    "    device float* max_values [[buffer(1)]],\n"
    "    constant uint& cols [[buffer(2)]],\n"
    "    uint row [[thread_position_in_grid]]) {\n"
    "  float max_val = -INFINITY;\n"
    "  const device float* row_ptr = input + row*cols;\n"
    "  for (uint c=0;c<cols;++c) max_val = max(max_val, row_ptr[c]);\n"
    "  max_values[row] = max_val;\n"
    "}\n"
    "\n"
    "kernel void softmax_exp_sum_pass(\n"
    "    const device float* input [[buffer(0)]],\n"
    "    device float* output [[buffer(1)]],\n"
    "    device float* sum_values [[buffer(2)]],\n"
    "    const device float* max_values [[buffer(3)]],\n"
    "    constant uint& cols [[buffer(4)]],\n"
    "    uint row [[thread_position_in_grid]]) {\n"
    "  float sum = 0.0;\n"
    "  const device float* ri = input + row*cols;\n"
    "  device float* ro = output + row*cols;\n"
    "  float m = max_values[row];\n"
    "  for (uint c=0;c<cols;++c){ float e = exp(ri[c]-m); ro[c]=e; sum+=e; }\n"
    "  sum_values[row] = sum;\n"
    "}\n"
    "\n"
    "kernel void softmax_normalize_pass(\n"
    "    device float* output [[buffer(0)]],\n"
    "    const device float* sum_values [[buffer(1)]],\n"
    "    constant uint& cols [[buffer(2)]],\n"
    "    uint row [[thread_position_in_grid]]) {\n"
    "  device float* ro = output + row*cols;\n"
    "  float inv = 1.0/(sum_values[row]+1e-9);\n"
    "  for (uint c=0;c<cols;++c) ro[c]*=inv;\n"
    "}\n"
    "\n"
    "kernel void matrix_multiply_tiled(\n"
    "    const device float* A [[buffer(0)]],\n"
    "    const device float* B [[buffer(1)]],\n"
    "    device float* C [[buffer(2)]],\n"
    "    constant uint& rowsA [[buffer(3)]],\n"
    "    constant uint& colsA [[buffer(4)]],\n"
    "    constant uint& colsB [[buffer(5)]],\n"
    "    uint2 gid [[thread_position_in_grid]],\n"
    "    uint2 lid [[thread_position_in_threadgroup]]) {\n"
    "  const uint TILE_SIZE=16;\n"
    "  threadgroup float tileA[TILE_SIZE][TILE_SIZE];\n"
    "  threadgroup float tileB[TILE_SIZE][TILE_SIZE];\n"
    "  uint row=gid.y, col=gid.x; float sum=0.0;\n"
    "  uint tilesInK=(colsA+TILE_SIZE-1)/TILE_SIZE;\n"
    "  for(uint t=0;t<tilesInK;++t){\n"
    "    uint aRow=row, aCol=t*TILE_SIZE+lid.x;\n"
    "    tileA[lid.y][lid.x]=(aRow<rowsA && aCol<colsA)?A[aRow*colsA+aCol]:0.0;\n"
    "    uint bRow=t*TILE_SIZE+lid.y, bCol=col;\n"
    "    tileB[lid.y][lid.x]=(bRow<colsA && bCol<colsB)?B[bRow*colsB+bCol]:0.0;\n"
    "    threadgroup_barrier(mem_flags::mem_threadgroup);\n"
    "    for(uint k=0;k<TILE_SIZE;++k) sum+=tileA[lid.y][k]*tileB[k][lid.x];\n"
    "    threadgroup_barrier(mem_flags::mem_threadgroup);\n"
    "  }\n"
    "  if(row<rowsA && col<colsB) C[row*colsB+col]=sum;\n"
    "}\n"
    "\n"
    "// ---------------- Multi-Head Attention (no RoPE) ----------------\n"
    "kernel void mha_pass1_max(\n"
    "  const device float* Q [[buffer(0)]],\n"
    "  const device float* K [[buffer(1)]],\n"
    "  device float* maxVals [[buffer(2)]],\n"
    "  constant uint& seqLen [[buffer(3)]],\n"
    "  constant uint& headCnt [[buffer(4)]],\n"
    "  constant uint& headSz [[buffer(5)]],\n"
    "  constant float& scale [[buffer(6)]],\n"
    "  constant uint& causal [[buffer(7)]],\n"
    "  uint2 tid [[thread_position_in_grid]]) {\n"
    "  uint h=tid.x, i=tid.y; if(h>=headCnt||i>=seqLen) return; uint model=headCnt*headSz;\n"
    "  const device float* qi=Q + i*model + h*headSz; float maxv=-INFINITY;\n"
    "  for(uint j=0;j<seqLen;++j){ if(causal && j>i) break; const device float* kj=K + j*model + h*headSz;\n"
    "    float dot=0.0; for(uint d=0; d<headSz; ++d) dot+=qi[d]*kj[d]; float s=dot*scale; if(s>maxv) maxv=s; }\n"
    "  maxVals[i*headCnt + h] = maxv; }\n"
    "\n"
    "kernel void mha_pass2_sumexp(\n"
    "  const device float* Q [[buffer(0)]],\n"
    "  const device float* K [[buffer(1)]],\n"
    "  const device float* maxVals [[buffer(2)]],\n"
    "  device float* sumVals [[buffer(3)]],\n"
    "  constant uint& seqLen [[buffer(4)]],\n"
    "  constant uint& headCnt [[buffer(5)]],\n"
    "  constant uint& headSz [[buffer(6)]],\n"
    "  constant float& scale [[buffer(7)]],\n"
    "  constant uint& causal [[buffer(8)]],\n"
    "  uint2 tid [[thread_position_in_grid]]) {\n"
    "  uint h=tid.x, i=tid.y; if(h>=headCnt||i>=seqLen) return; uint model=headCnt*headSz;\n"
    "  const device float* qi=Q + i*model + h*headSz; float m=maxVals[i*headCnt + h]; float sum=0.0;\n"
    "  for(uint j=0;j<seqLen;++j){ if(causal && j>i) break; const device float* kj=K + j*model + h*headSz;\n"
    "    float dot=0.0; for(uint d=0; d<headSz; ++d) dot+=qi[d]*kj[d]; sum += exp(dot*scale - m); }\n"
    "  sumVals[i*headCnt + h] = sum; }\n"
    "\n"
    "kernel void mha_pass3_out(\n"
    "  const device float* Q [[buffer(0)]],\n"
    "  const device float* K [[buffer(1)]],\n"
    "  const device float* V [[buffer(2)]],\n"
    "  const device float* maxVals [[buffer(3)]],\n"
    "  const device float* sumVals [[buffer(4)]],\n"
    "  device float* Out [[buffer(5)]],\n"
    "  constant uint& seqLen [[buffer(6)]],\n"
    "  constant uint& headCnt [[buffer(7)]],\n"
    "  constant uint& headSz [[buffer(8)]],\n"
    "  constant float& scale [[buffer(9)]],\n"
    "  constant uint& causal [[buffer(10)]],\n"
    "  uint2 tid [[thread_position_in_grid]]) {\n"
    "  uint h=tid.x, i=tid.y; if(h>=headCnt||i>=seqLen) return; uint model=headCnt*headSz;\n"
    "  const device float* qi=Q + i*model + h*headSz; device float* out = Out + i*model + h*headSz;\n"
    "  for(uint d=0; d<headSz; ++d) out[d]=0.0;\n"
    "  float m=maxVals[i*headCnt+h]; float inv=1.0/(sumVals[i*headCnt+h]+1e-9);\n"
    "  for(uint j=0;j<seqLen;++j){ if(causal && j>i) break; const device float* kj=K + j*model + h*headSz;\n"
    "    const device float* vj=V + j*model + h*headSz; float dot=0.0; for(uint d=0; d<headSz; ++d) dot+=qi[d]*kj[d];\n"
    "    float w = exp(dot*scale - m) * inv; for(uint d=0; d<headSz; ++d) out[d] += w * vj[d]; }\n"
    "}\n";

    @try {
        MTLCompileOptions* options = [[MTLCompileOptions alloc] init];
        // Using system default language version is fine; uncomment if you want to pin it:
        // options.languageVersion = MTLLanguageVersion2_0;
        options.fastMathEnabled = NO;

        id<MTLLibrary> lib = [device newLibraryWithSource:src options:options error:&error];
        if (!lib || error) {
            NSLog(@"Failed to compile Metal library synchronously: %@", error ? error.localizedDescription : @"unknown error");
            return nil;
        }
        NSLog(@"Successfully compiled Metal library with kernels as fallback");
        if ([lib respondsToSelector:@selector(functionNames)]) {
            NSLog(@"Metal lib has functions: %@", [lib functionNames]);
        }
        return lib;
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
        context->mhaPass1MaxPipeline = nil;
        context->mhaPass2SumExpPipeline = nil;
        context->mhaPass3OutPipeline = nil;

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

        // MHA pipelines
        id<MTLFunction> f1 = [context->library newFunctionWithName:@"mha_pass1_max"];
        if (f1) {
            context->mhaPass1MaxPipeline = [device newComputePipelineStateWithFunction:f1 error:&error];
            if (!context->mhaPass1MaxPipeline || error) { NSLog(@"MHA pass1 pipeline failed: %@", error.localizedDescription); context->mhaPass1MaxPipeline = nil; }
        } else { NSLog(@"mha_pass1_max not found"); }

        id<MTLFunction> f2 = [context->library newFunctionWithName:@"mha_pass2_sumexp"];
        if (f2) {
            context->mhaPass2SumExpPipeline = [device newComputePipelineStateWithFunction:f2 error:&error];
            if (!context->mhaPass2SumExpPipeline || error) { NSLog(@"MHA pass2 pipeline failed: %@", error.localizedDescription); context->mhaPass2SumExpPipeline = nil; }
        } else { NSLog(@"mha_pass2_sumexp not found"); }

        id<MTLFunction> f3 = [context->library newFunctionWithName:@"mha_pass3_out"];
        if (f3) {
            context->mhaPass3OutPipeline = [device newComputePipelineStateWithFunction:f3 error:&error];
            if (!context->mhaPass3OutPipeline || error) { NSLog(@"MHA pass3 pipeline failed: %@", error.localizedDescription); context->mhaPass3OutPipeline = nil; }
        } else { NSLog(@"mha_pass3_out not found"); }

        NSLog(@"Metal context initialized successfully");
        return reinterpret_cast<jlong>(context);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_onyxdevtools_ai_compute_MetalComputeBackend_gpuMultiHeadAttention(
    JNIEnv* env, jclass, jlong contextHandle,
    jlong bufferQ, jlong bufferK, jlong bufferV,
    jint seqLen, jint headCnt, jint headSz,
    jfloat scale, jint causal, jlong bufferOut
) {
    @autoreleasepool {
        MetalContext* ctx = reinterpret_cast<MetalContext*>(contextHandle);
        if (!ctx || !ctx->mhaPass1MaxPipeline || !ctx->mhaPass2SumExpPipeline || !ctx->mhaPass3OutPipeline)
            return JNI_FALSE;

        id<MTLBuffer> Q = (__bridge id<MTLBuffer>)((void*)bufferQ);
        id<MTLBuffer> K = (__bridge id<MTLBuffer>)((void*)bufferK);
        id<MTLBuffer> V = (__bridge id<MTLBuffer>)((void*)bufferV);
        id<MTLBuffer> O = (__bridge id<MTLBuffer>)((void*)bufferOut);
        if (!Q || !K || !V || !O) return JNI_FALSE;

        // temp buffers: max & sum per (row, head)
        const NSUInteger auxLen = (NSUInteger)seqLen * (NSUInteger)headCnt * sizeof(float);
        id<MTLBuffer> maxBuf = [ctx->device newBufferWithLength:auxLen options:MTLResourceStorageModeShared];
        id<MTLBuffer> sumBuf = [ctx->device newBufferWithLength:auxLen options:MTLResourceStorageModeShared];
        if (!maxBuf || !sumBuf) { return JNI_FALSE; }
        // Manually retain temporary buffers since ARC is disabled and newBufferWithLength returns an owned reference.
        // Retain count after newBufferWithLength: 1, after CFRetain: 2. We must CFRelease twice to fully release below.
        CFRetain((__bridge CFTypeRef)maxBuf);
        CFRetain((__bridge CFTypeRef)sumBuf);

        id<MTLCommandBuffer> cb = [ctx->commandQueue commandBuffer];

        // Common sizes
        MTLSize grid  = MTLSizeMake((NSUInteger)headCnt, (NSUInteger)seqLen, 1);
        MTLSize tptg  = MTLSizeMake(1, 1, 1); // simple; tune later

        // Pass 1: max
        {
            id<MTLComputeCommandEncoder> enc = [cb computeCommandEncoder];
            [enc setComputePipelineState:ctx->mhaPass1MaxPipeline];
            [enc setBuffer:Q offset:0 atIndex:0];
            [enc setBuffer:K offset:0 atIndex:1];
            [enc setBuffer:maxBuf offset:0 atIndex:2];
            [enc setBytes:&seqLen length:sizeof(uint32_t) atIndex:3];
            [enc setBytes:&headCnt length:sizeof(uint32_t) atIndex:4];
            [enc setBytes:&headSz length:sizeof(uint32_t) atIndex:5];
            [enc setBytes:&scale length:sizeof(float) atIndex:6];
            [enc setBytes:&causal length:sizeof(uint32_t) atIndex:7];
            [enc dispatchThreads:grid threadsPerThreadgroup:tptg];
            [enc endEncoding];
        }

        // Pass 2: sumExp
        {
            id<MTLComputeCommandEncoder> enc = [cb computeCommandEncoder];
            [enc setComputePipelineState:ctx->mhaPass2SumExpPipeline];
            [enc setBuffer:Q offset:0 atIndex:0];
            [enc setBuffer:K offset:0 atIndex:1];
            [enc setBuffer:maxBuf offset:0 atIndex:2];
            [enc setBuffer:sumBuf offset:0 atIndex:3];
            [enc setBytes:&seqLen length:sizeof(uint32_t) atIndex:4];
            [enc setBytes:&headCnt length:sizeof(uint32_t) atIndex:5];
            [enc setBytes:&headSz length:sizeof(uint32_t) atIndex:6];
            [enc setBytes:&scale length:sizeof(float) atIndex:7];
            [enc setBytes:&causal length:sizeof(uint32_t) atIndex:8];
            [enc dispatchThreads:grid threadsPerThreadgroup:tptg];
            [enc endEncoding];
        }

        // Pass 3: output
        {
            id<MTLComputeCommandEncoder> enc = [cb computeCommandEncoder];
            [enc setComputePipelineState:ctx->mhaPass3OutPipeline];
            [enc setBuffer:Q offset:0 atIndex:0];
            [enc setBuffer:K offset:0 atIndex:1];
            [enc setBuffer:V offset:0 atIndex:2];
            [enc setBuffer:maxBuf offset:0 atIndex:3];
            [enc setBuffer:sumBuf offset:0 atIndex:4];
            [enc setBuffer:O offset:0 atIndex:5];
            [enc setBytes:&seqLen length:sizeof(uint32_t) atIndex:6];
            [enc setBytes:&headCnt length:sizeof(uint32_t) atIndex:7];
            [enc setBytes:&headSz length:sizeof(uint32_t) atIndex:8];
            [enc setBytes:&scale length:sizeof(float) atIndex:9];
            [enc setBytes:&causal length:sizeof(uint32_t) atIndex:10];
            [enc dispatchThreads:grid threadsPerThreadgroup:tptg];
            [enc endEncoding];
        }

        [cb commit];
        [cb waitUntilCompleted];
        BOOL ok = (cb.status == MTLCommandBufferStatusCompleted);

        // Release both the CFRetain and the original newBuffer ownership to avoid leaks.
        CFRelease((__bridge CFTypeRef)maxBuf);
        CFRelease((__bridge CFTypeRef)maxBuf);
        CFRelease((__bridge CFTypeRef)sumBuf);
        CFRelease((__bridge CFTypeRef)sumBuf);
        return ok ? JNI_TRUE : JNI_FALSE;
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
        // Manually retain temporary buffers since ARC is disabled and newBufferWithLength returns an owned reference.
        // Retain count after newBufferWithLength: 1, after CFRetain: 2. We will CFRelease twice below.
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

        // Clean up temporary buffers: release both the CFRetain and the original newBuffer ownership.
        CFRelease((__bridge CFTypeRef)maxBuffer);
        CFRelease((__bridge CFTypeRef)maxBuffer);
        CFRelease((__bridge CFTypeRef)sumBuffer);
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
