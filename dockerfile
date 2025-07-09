# Don't think this matters
FROM ubuntu:20.04

# Install additional dependencies
USER root
RUN apt-get update && apt-get install -y git build-essential cmake

# Copy necessary project files
WORKDIR /app
COPY ggml-tiny.bin .
COPY CMakeLists.txt .
COPY .git ./.git
COPY src ./src
COPY build_debian.sh .

# Init submodules
RUN git submodule update --init

# Optional: run custom native build
RUN ./build_debian.sh
