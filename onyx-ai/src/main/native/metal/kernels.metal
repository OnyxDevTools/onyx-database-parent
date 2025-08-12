#include <metal_stdlib>
using namespace metal;

// Matrix multiplication kernel
kernel void matrix_multiply(
    const device float* A [[buffer(0)]],
    const device float* B [[buffer(1)]],
    device float* C [[buffer(2)]],
    constant uint& rowsA [[buffer(3)]],
    constant uint& colsA [[buffer(4)]],
    constant uint& colsB [[buffer(5)]],
    uint2 gid [[thread_position_in_grid]]
) {
    uint row = gid.y;
    uint col = gid.x;
    
    if (row >= rowsA || col >= colsB) return;
    
    float sum = 0.0;
    for (uint k = 0; k < colsA; k++) {
        sum += A[row * colsA + k] * B[k * colsB + col];
    }
    
    C[row * colsB + col] = sum;
}

// Matrix addition kernel
kernel void matrix_add(
    const device float* A [[buffer(0)]],
    const device float* B [[buffer(1)]],
    device float* C [[buffer(2)]],
    uint index [[thread_position_in_grid]]
) {
    C[index] = A[index] + B[index];
}

// Matrix subtraction kernel
kernel void matrix_subtract(
    const device float* A [[buffer(0)]],
    const device float* B [[buffer(1)]],
    device float* C [[buffer(2)]],
    uint index [[thread_position_in_grid]]
) {
    C[index] = A[index] - B[index];
}

// Element-wise multiplication kernel
kernel void matrix_element_multiply(
    const device float* A [[buffer(0)]],
    const device float* B [[buffer(1)]],
    device float* C [[buffer(2)]],
    uint index [[thread_position_in_grid]]
) {
    C[index] = A[index] * B[index];
}

// Matrix transpose kernel
kernel void matrix_transpose(
    const device float* input [[buffer(0)]],
    device float* output [[buffer(1)]],
    constant uint& rows [[buffer(2)]],
    constant uint& cols [[buffer(3)]],
    uint2 gid [[thread_position_in_grid]]
) {
    uint row = gid.y;
    uint col = gid.x;
    
    if (row >= rows || col >= cols) return;
    
    output[col * rows + row] = input[row * cols + col];
}

// Softmax kernel - first pass to find max
kernel void softmax_max_pass(
    const device float* input [[buffer(0)]],
    device float* max_values [[buffer(1)]],
    constant uint& cols [[buffer(2)]],
    uint row [[thread_position_in_grid]]
) {
    float max_val = -INFINITY;
    const device float* row_ptr = input + row * cols;
    
    for (uint col = 0; col < cols; col++) {
        max_val = max(max_val, row_ptr[col]);
    }
    
    max_values[row] = max_val;
}

// Softmax kernel - second pass to compute exp and sum
kernel void softmax_exp_sum_pass(
    const device float* input [[buffer(0)]],
    device float* output [[buffer(1)]],
    device float* sum_values [[buffer(2)]],
    const device float* max_values [[buffer(3)]],
    constant uint& cols [[buffer(4)]],
    uint row [[thread_position_in_grid]]
) {
    float sum = 0.0;
    const device float* row_input = input + row * cols;
    device float* row_output = output + row * cols;
    float max_val = max_values[row];
    
    // Compute exp(x - max) and accumulate sum
    for (uint col = 0; col < cols; col++) {
        float exp_val = exp(row_input[col] - max_val);
        row_output[col] = exp_val;
        sum += exp_val;
    }
    
    sum_values[row] = sum;
}

// Softmax kernel - third pass to normalize
kernel void softmax_normalize_pass(
    device float* output [[buffer(0)]],
    const device float* sum_values [[buffer(1)]],
    constant uint& cols [[buffer(2)]],
    uint row [[thread_position_in_grid]]
) {
    device float* row_output = output + row * cols;
    float sum = sum_values[row];
    
    for (uint col = 0; col < cols; col++) {
        row_output[col] /= sum;
    }
}

// Optimized matrix multiplication with tiling
kernel void matrix_multiply_tiled(
    const device float* A [[buffer(0)]],
    const device float* B [[buffer(1)]],
    device float* C [[buffer(2)]],
    constant uint& rowsA [[buffer(3)]],
    constant uint& colsA [[buffer(4)]],
    constant uint& colsB [[buffer(5)]],
    uint2 gid [[thread_position_in_grid]],
    uint2 local_id [[thread_position_in_threadgroup]]
) {
    const uint TILE_SIZE = 16;
    
    threadgroup float tileA[TILE_SIZE][TILE_SIZE];
    threadgroup float tileB[TILE_SIZE][TILE_SIZE];
    
    uint row = gid.y;
    uint col = gid.x;
    
    float sum = 0.0;
    
    uint tilesInK = (colsA + TILE_SIZE - 1) / TILE_SIZE;
    
    for (uint tileIdx = 0; tileIdx < tilesInK; tileIdx++) {
        // Load tile A
        uint aRow = row;
        uint aCol = tileIdx * TILE_SIZE + local_id.x;
        if (aRow < rowsA && aCol < colsA) {
            tileA[local_id.y][local_id.x] = A[aRow * colsA + aCol];
        } else {
            tileA[local_id.y][local_id.x] = 0.0;
        }
        
        // Load tile B
        uint bRow = tileIdx * TILE_SIZE + local_id.y;
        uint bCol = col;
        if (bRow < colsA && bCol < colsB) {
            tileB[local_id.y][local_id.x] = B[bRow * colsB + bCol];
        } else {
            tileB[local_id.y][local_id.x] = 0.0;
        }
        
        threadgroup_barrier(mem_flags::mem_threadgroup);
        
        // Compute partial sum
        for (uint k = 0; k < TILE_SIZE; k++) {
            sum += tileA[local_id.y][k] * tileB[k][local_id.x];
        }
        
        threadgroup_barrier(mem_flags::mem_threadgroup);
    }
    
    if (row < rowsA && col < colsB) {
        C[row * colsB + col] = sum;
    }
}
