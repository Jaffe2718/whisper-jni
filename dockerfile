# Don't think this matters
FROM gradle:8.12.1-jdk21 as builder

# Disable interactive prompts for apt-get
ENV DEBIAN_FRONTEND=noninteractive

# Install additional dependencies
USER root
RUN apt-get update && apt-get install -y git build-essential cmake

# Copy necessary project files
WORKDIR /app
COPY ggml-tiny.bin .
COPY CMakeLists.txt .
COPY .git ./.git
COPY src ./src
COPY build.gradle .
COPY build_debian.sh .

# Init submodules
RUN git submodule update --init

# Optional: run custom native build
RUN ./build_debian.sh

# Test. We use the gradle tool not the wrapper (we're not copying the entire project in)
RUN gradle test
