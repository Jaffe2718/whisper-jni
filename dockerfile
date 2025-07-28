# Don't think this matters
FROM gradle:8.12.1-jdk21 AS builder

# Disable interactive prompts for apt-get
ENV DEBIAN_FRONTEND=noninteractive

# Install additional dependencies
USER root
# Install basic dependencies
RUN apt-get update && apt-get install -y \
    git \
    build-essential \
    cmake \
    # BEGIN NEW SHIT
    gnupg \
    curl \
    lsb-release \
    && rm -rf /var/lib/apt/lists/*

# Add Vulkan SDK repo apparently
RUN curl -sSL https://packages.lunarg.com/lunarg-signing-key.asc | apt-key add - \
    && DISTRO=$(lsb_release -c | awk '{print $2}') \
    && echo "deb https://packages.lunarg.com/vulkan/1.4.309.0/debian/$DISTRO/amd64 /" > /etc/apt/sources.list.d/lunarg-vulkan.list \
    && apt-get update

# Install Vulkan tools and dev libraries
RUN apt-get install -y \
    vulkan-utils \
    libvulkan-dev \
    && rm -rf /var/lib/apt/lists/*

# RUN apt-get update && apt-get install -y git build-essential cmake
ENV VULKAN_ARG=OFF

# Set up Vulkan SDK if required
RUN if [ "$VULKAN_ARG" = "ON" ]; then \
    # Replace with the actual Vulkan SDK download URL and version
    VULKAN_SDK_VERSION="1.4.309.0" && \
    wget https://vulkan.lunarg.com/sdk/home#sdk/download/1.4.309.0/linux -O vulkan-sdk.tar.gz && \
    tar -xvf vulkan-sdk.tar.gz && \
    mv vulkan-sdk-linux-x.x.x.x /opt/vulkan-sdk && \
    rm vulkan-sdk.tar.gz; \
    fi

# Set Vulkan environment variables if installed
ENV VULKAN_SDK /opt/vulkan-sdk
ENV PATH $VULKAN_SDK/bin:$PATH
ENV LD_LIBRARY_PATH $VULKAN_SDK/lib:$LD_LIBRARY_PATH

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

# This will run not on `docker build`, but on `docker run`
# This is because we need to build the natives AFTER we define mounts so nothing gets overridden
# ... a little ugly but gets the job done
CMD ["/bin/bash", "-c", "./build_debian.sh && gradle test"]
