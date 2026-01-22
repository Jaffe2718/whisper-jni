$env:MODEL_NAME = 'silero-v6.2.0'
.\src\main\native\whisper\models\download-vad-model.cmd $env:MODEL_NAME
mv .\src\main\native\whisper\models\ggml-$env:MODEL_NAME.bin .\