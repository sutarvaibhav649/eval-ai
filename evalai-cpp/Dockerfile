# ---------------- Build ----------------
FROM ubuntu:22.04 AS build

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    cmake build-essential git pkg-config \
    libssl-dev libopencv-dev \
    && rm -rf /var/lib/apt/lists/*

# Build gRPC from source — includes protobuf, absl, utf8_range as submodules
RUN git clone --recurse-submodules -b v1.62.0 https://github.com/grpc/grpc /grpc \
    && cmake -S /grpc -B /grpc/cmake/build \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX=/usr/local \
        -DgRPC_INSTALL=ON \
        -DgRPC_BUILD_TESTS=OFF \
        -DBUILD_SHARED_LIBS=OFF \
    && cmake --build /grpc/cmake/build --target install -j$(nproc) \
    && ldconfig

WORKDIR /app

COPY CMakeLists.txt .
COPY proto ./proto
COPY src ./src

RUN cmake -B build -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_PREFIX_PATH=/usr/local \
    && cmake --build build --config Release -j$(nproc)

# ---------------- Run ----------------
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    libopencv-dev \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /usr/local /usr/local
RUN ldconfig

WORKDIR /app
COPY --from=build /app/build/evalai_cpp .

EXPOSE 50051
CMD ["./evalai_cpp"]