@echo off
chcp 65001 >nul
"C:\Users\yonom\AppData\Local\Llamacpp\cpu\llama-server.exe" ^
  -m "C:\Users\yonom\OneDrive\Escritorio\Investigacion\Modelos\Llama-3.2-1B-Instruct-F16.gguf" ^
  -c 4096 ^
  --host 127.0.0.1 ^
  --port 8080