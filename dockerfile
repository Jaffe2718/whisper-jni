# Browse at https://hub.docker.com/_/gradle/tags
FROM gradle:8.12.1-jdk21 as builder

# Install additional dependencies
USER root
RUN apt-get update && apt-get install -y git build-essential make

# Optionally get newer CMake (if needed for your build_debian.sh)
#RUN curl -s https://apt.kitware.com/kitware-archive.sh | bash -s
#RUN apt-get update && apt-get install -y cmake

# Copy necessary project files
WORKDIR /app
COPY ggml-tiny.bin .
COPY build.gradle .
COPY CMakeLists.txt .
COPY .git ./.git
COPY src ./src
COPY build_debian.sh .

# Init submodules
RUN git submodule update --init

# Optional: run custom native build
RUN ./build_debian.sh

# Build project with Gradle
#RUN gradle build

# Optionally run tests if RUN_TESTS is set
ARG RUN_TESTS
RUN if [ "$RUN_TESTS" = "true" ]; then gradle test; fi
