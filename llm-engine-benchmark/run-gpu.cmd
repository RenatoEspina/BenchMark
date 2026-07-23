@echo off
setlocal
chcp 65001 >nul
set TORNADO_SDK=C:\Users\yonom\AppData\Local\TornadoVM\tornadovm-5.0.0-jdk25-cuda

"C:\Program Files\Java\jdk-25.0.3\bin\java" ^
  -server -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI ^
  -Xms16g -Xmx16g ^
  --enable-preview ^
  -Djava.library.path=%TORNADO_SDK%/lib ^
  -Djdk.module.showModuleResolution=false ^
  --module-path .;%TORNADO_SDK%/share/java/tornado ^
  -Dtornado.load.api.implementation=uk.ac.manchester.tornado.runtime.tasks.TornadoTaskGraph ^
  -Dtornado.load.runtime.implementation=uk.ac.manchester.tornado.runtime.TornadoCoreRuntime ^
  -Dtornado.load.tornado.implementation=uk.ac.manchester.tornado.runtime.common.Tornado ^
  -Dtornado.load.annotation.implementation=uk.ac.manchester.tornado.annotation.ASMClassVisitor ^
  -Dtornado.load.annotation.parallel=uk.ac.manchester.tornado.api.annotations.Parallel ^
  -Dtornado.tvm.maxbytecodesize=65536 ^
  -Duse.tornadovm=true ^
  -Dtornado.threadInfo=false ^
  -Dtornado.debug=false ^
  -Dtornado.fullDebug=false ^
  -Dtornado.printKernel=false ^
  -Dtornado.print.bytecodes=false ^
  -Dtornado.device.memory=6GB ^
  -Dtornado.profiler=false ^
  -Dtornado.log.profiler=false ^
  -Dtornado.enable.fastMathOptimizations=true ^
  -Dtornado.enable.mathOptimizations=false ^
  -Dtornado.enable.nativeFunctions=true ^
  -Dtornado.loop.interchange=true ^
  -Dtornado.eventpool.maxwaitevents=32000 ^
  --upgrade-module-path %TORNADO_SDK%/share/java/graalJars ^
  @%TORNADO_SDK%/etc/exportLists/common-exports ^
  @%TORNADO_SDK%/etc/exportLists/cuda-exports ^
  --add-modules ALL-SYSTEM,jdk.incubator.vector,tornado.runtime,tornado.annotation,tornado.drivers.common,tornado.drivers.cuda ^
  --enable-native-access=tornado.drivers.cuda ^
  -Dgpullama3.onGPU=true ^
  -jar benchmark-app\target\benchmark-app.jar

endlocal