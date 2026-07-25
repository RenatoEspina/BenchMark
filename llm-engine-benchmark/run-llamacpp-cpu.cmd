@echo off
chcp 65001 >nul
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE=java"
"%JAVA_EXE%" ^
  -Dfile.encoding=UTF-8 ^
  -Dllamacpp.binary="C:\Users\yonom\AppData\Local\Llamacpp\cpu\llama-cli.exe" ^
  -Dllamacpp.ctxSize=4096 ^
  -jar benchmark-app\target\benchmark-app.jar