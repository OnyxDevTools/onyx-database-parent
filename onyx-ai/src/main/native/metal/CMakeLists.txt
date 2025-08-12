cmake_minimum_required(VERSION 3.10)
project(onyx-metal)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find required frameworks on macOS
if(APPLE)
    find_library(METAL_FRAMEWORK Metal)
    find_library(FOUNDATION_FRAMEWORK Foundation)
    find_library(COREGRAPHICS_FRAMEWORK CoreGraphics)
    
    if(NOT METAL_FRAMEWORK)
        message(FATAL_ERROR "Metal framework not found")
    endif()
    
    if(NOT FOUNDATION_FRAMEWORK)
        message(FATAL_ERROR "Foundation framework not found")  
    endif()
    
    # Find JNI
    find_package(JNI REQUIRED)
    
    # Set source files
    set(SOURCES
        OnyxMetal.mm
    )
    
    # Create shared library
    add_library(onyx-metal SHARED ${SOURCES})
    
    # Link frameworks
    target_link_libraries(onyx-metal 
        ${METAL_FRAMEWORK}
        ${FOUNDATION_FRAMEWORK}
        ${COREGRAPHICS_FRAMEWORK}
        ${JNI_LIBRARIES}
    )
    
    # Include directories
    target_include_directories(onyx-metal PRIVATE 
        ${JNI_INCLUDE_DIRS}
    )
    
    # Set properties
    set_target_properties(onyx-metal PROPERTIES
        MACOSX_BUNDLE TRUE
        FRAMEWORK FALSE
        BUNDLE_EXTENSION "dylib"
    )
    
    # Compile Metal shaders to metallib
    add_custom_command(
        OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/kernels.metallib
        COMMAND xcrun -sdk macosx metal -c ${CMAKE_CURRENT_SOURCE_DIR}/kernels.metal -o ${CMAKE_CURRENT_BINARY_DIR}/kernels.air
        COMMAND xcrun -sdk macosx metallib ${CMAKE_CURRENT_BINARY_DIR}/kernels.air -o ${CMAKE_CURRENT_BINARY_DIR}/kernels.metallib
        DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/kernels.metal
        COMMENT "Compiling Metal shaders"
    )
    
    # Custom target for Metal shaders
    add_custom_target(metal-shaders ALL
        DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/kernels.metallib
    )
    
    # Make sure shaders are built before the library
    add_dependencies(onyx-metal metal-shaders)
    
    # Install the library and metallib
    install(TARGETS onyx-metal
        LIBRARY DESTINATION lib
        RUNTIME DESTINATION bin
    )
    
    install(FILES ${CMAKE_CURRENT_BINARY_DIR}/kernels.metallib
        DESTINATION lib
    )
    
else()
    message(WARNING "Metal backend can only be built on macOS")
endif()
