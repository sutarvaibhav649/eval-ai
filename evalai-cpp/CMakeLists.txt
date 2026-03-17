cmake_minimum_required(VERSION 3.20)
project(evalai_cpp VERSION 1.0.0)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ----------------------------------------------------------
# Find packages
# ----------------------------------------------------------

# OpenCV
set(OpenCV_DIR "C:/opencv/build")
find_package(OpenCV REQUIRED)

# gRPC and Protobuf via vcpkg
find_package(gRPC CONFIG REQUIRED)
find_package(Protobuf CONFIG REQUIRED)

# ----------------------------------------------------------
# Proto file generation
# ----------------------------------------------------------

set(PROTO_FILE "${CMAKE_SOURCE_DIR}/proto/preprocessing.proto")
set(GENERATED_DIR "${CMAKE_SOURCE_DIR}/generated")

# Get protoc and grpc_cpp_plugin paths from vcpkg
get_target_property(PROTOC_PATH protobuf::protoc IMPORTED_LOCATION_RELEASE)
get_target_property(GRPC_PLUGIN_PATH gRPC::grpc_cpp_plugin IMPORTED_LOCATION_RELEASE)

# Generate protobuf C++ files
set(PROTO_SRC "${GENERATED_DIR}/preprocessing.pb.cc")
set(PROTO_HDR "${GENERATED_DIR}/preprocessing.pb.h")
set(GRPC_SRC  "${GENERATED_DIR}/preprocessing.grpc.pb.cc")
set(GRPC_HDR  "${GENERATED_DIR}/preprocessing.grpc.pb.h")

add_custom_command(
    OUTPUT ${PROTO_SRC} ${PROTO_HDR} ${GRPC_SRC} ${GRPC_HDR}
    COMMAND ${PROTOC_PATH}
        --proto_path=${CMAKE_SOURCE_DIR}/proto
        --cpp_out=${GENERATED_DIR}
        --grpc_out=${GENERATED_DIR}
        --plugin=protoc-gen-grpc=${GRPC_PLUGIN_PATH}
        ${PROTO_FILE}
    DEPENDS ${PROTO_FILE}
    COMMENT "Generating C++ protobuf and gRPC files"
)

# ----------------------------------------------------------
# Source files
# ----------------------------------------------------------

set(SOURCES
    src/main.cpp
    src/server/PreprocessingServiceImpl.cpp
    src/preprocessing/ImageProcessor.cpp
    src/preprocessing/Deskewer.cpp
    src/preprocessing/Anonymizer.cpp
    ${PROTO_SRC}
    ${GRPC_SRC}
)

# ----------------------------------------------------------
# Executable
# ----------------------------------------------------------
add_executable(evalai_cpp ${SOURCES})

target_include_directories(evalai_cpp PRIVATE
    ${CMAKE_SOURCE_DIR}/src
    ${CMAKE_SOURCE_DIR}/generated
    ${OpenCV_INCLUDE_DIRS}
)

target_link_libraries(evalai_cpp PRIVATE
    gRPC::grpc++
    gRPC::grpc
    protobuf::libprotobuf
    ${OpenCV_LIBS}
)

# ----------------------------------------------------------
# Windows specific
# ----------------------------------------------------------

# Fix OpenCV path
set(OpenCV_DIR "C:/opencv/opencv/build")

# Fix Windows DLL copy at bottom
if(WIN32)
    add_custom_command(TARGET evalai_cpp POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
            "C:/opencv/opencv/build/x64/vc16/bin/opencv_world490.dll"
            $<TARGET_FILE_DIR:evalai_cpp>
    )
endif()