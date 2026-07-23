@echo off
chcp 65001 >nul
"C:\Program Files\Java\jdk-25.0.3\bin\java" ^
  -Dfile.encoding=UTF-8 ^
  -Dllamacpp.binary="C:\Users\yonom\AppData\Local\Llamacpp\cuda\llama-cli.exe" ^
  -Dllamacpp.ctxSize=4096 ^
  -Dllamacpp.nGpuLayers=999 ^
  -jar benchmark-app\target\benchmark-app.jar ^
  --engine=LLAMA_CPP ^
  --model=%1