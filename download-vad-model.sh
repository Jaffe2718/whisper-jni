#!/bin/sh

# This script downloads Whisper VAD model files that have already been converted
# to ggml format. This way you don't have to convert them yourself.

src="https://huggingface.co/ggml-org/whisper-vad"
pfx="resolve/main/ggml"

BOLD="\033[1m"
RESET='\033[0m'

default_download_path=./

models_path="${2:-$default_download_path}"

# Whisper VAD models
model="silero-v6.2.0"

# download ggml model
printf "Downloading ggml model %s from '%s' ...\n" "$model" "$src"

cd "$models_path" || exit

if [ -f "ggml-$model.bin" ]; then
    printf "Model %s already exists. Skipping download.\n" "$model"
    exit 0
fi

if [ -x "$(command -v wget2)" ]; then
    wget2 --no-config --progress bar -O ggml-"$model".bin $src/$pfx-"$model".bin
elif [ -x "$(command -v wget)" ]; then
    wget --no-config --quiet --show-progress -O ggml-"$model".bin $src/$pfx-"$model".bin
elif [ -x "$(command -v curl)" ]; then
    curl -L --output ggml-"$model".bin $src/$pfx-"$model".bin
else
    printf "Either wget or curl is required to download models.\n"
    exit 1
fi

if [ $? -ne 0 ]; then
    printf "Failed to download ggml model %s \n" "$model"
    printf "Please try again later or download the original Whisper model files and convert them yourself.\n"
    exit 1
fi

# Check if 'whisper-cli' is available in the system PATH
if command -v whisper-cli >/dev/null 2>&1; then
    # If found, use 'whisper-cli' (relying on PATH resolution)
    whisper_cmd="whisper-cli"
else
    # If not found, use the local build version
    whisper_cmd="./build/bin/whisper-cli"
fi

printf "Done! Model '%s' saved in '%s/ggml-%s.bin'\n" "$model" "$models_path" "$model"
printf "You can now use it like this:\n\n"
printf "  $ %s -vm %s/ggml-%s.bin --vad -f samples/jfk.wav -m models/ggml-base.en.bin\n" "$whisper_cmd" "$models_path" "$model"
printf "\n"
