@echo off
rem Thin client for the alfred daemon. Build the jar once with: gradlew :client:jar
set "JAVA_CMD=java"
if defined JAVA_HOME set "JAVA_CMD=%JAVA_HOME%\bin\java"
"%JAVA_CMD%" -jar "%~dp0..\client\build\libs\alfred-client.jar" %*
