cmake_minimum_required(VERSION 3.20)
project(evalai_cpp VERSION 1.0.0)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(OpenCV REQUIRED)
find_package(Protobuf REQUIRED)
find_package(gRPC REQUIRED)
find_package(absl REQUIRED)
# REMOVED: find_package(utf8_range REQUIRED)

find_program(PROTOC_EXECUTABLE protoc)
find_program(GRPC_CPP_PLUGIN_EXECUTABLE grpc_cpp_plugin)

set(PROTO_SRC_DIR "${CMAKE_SOURCE_DIR}/proto")
set(PROTO_OUT_DIR "${CMAKE_SOURCE_DIR}/generated")
file(MAKE_DIRECTORY ${PROTO_OUT_DIR})

set(PROTO_FILE "${PROTO_SRC_DIR}/preprocessing.proto")
set(GENERATED_PB_SRC "${PROTO_OUT_DIR}/preprocessing.pb.cc")
set(GENERATED_GRPC_SRC "${PROTO_OUT_DIR}/preprocessing.grpc.pb.cc")

add_custom_command(
    OUTPUT "${GENERATED_PB_SRC}" "${GENERATED_GRPC_SRC}"
    COMMAND ${PROTOC_EXECUTABLE}
    ARGS --proto_path=${PROTO_SRC_DIR}
        --cpp_out=${PROTO_OUT_DIR}
        --grpc_out=${PROTO_OUT_DIR}
        --plugin=protoc-gen-grpc=${GRPC_CPP_PLUGIN_EXECUTABLE}
        ${PROTO_FILE}
    DEPENDS ${PROTO_FILE}
    COMMENT "Building Proto and gRPC files..."
)

add_executable(evalai_cpp
    src/main.cpp
    src/server/PreprocessingServiceImpl.cpp
    src/preprocessing/ImageProcessor.cpp
    src/preprocessing/Deskewer.cpp
    src/preprocessing/Anonymizer.cpp
    ${GENERATED_PB_SRC}
    ${GENERATED_GRPC_SRC}
)

target_include_directories(evalai_cpp PRIVATE
    src
    ${PROTO_OUT_DIR}
    ${OpenCV_INCLUDE_DIRS}
)

target_link_libraries(evalai_cpp PRIVATE
    gRPC::grpc++
    protobuf::libprotobuf

    absl::base
    absl::strings
    absl::log
    absl::log_internal_check_op
    absl::log_internal_message
    absl::synchronization
    absl::throw_delegate

    # Link utf8_range by raw name — installed by gRPC source build to /usr/local/lib
    utf8_range
    utf8_validity

    ${OpenCV_LIBS}
)

if(UNIX)
    target_link_libraries(evalai_cpp PRIVATE pthread dl)
endif()