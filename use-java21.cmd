@echo off
rem Switches ONLY the current cmd window to Java 21. Usage:  use-java21
if defined JDK21_HOME (set "JDK=%JDK21_HOME%") else (set "JDK=%USERPROFILE%\.jdks\temurin-21.0.7")
if not exist "%JDK%\bin\java.exe" (
  echo JDK 21 not found at %JDK%. Set JDK21_HOME to your JDK 21 folder.
  exit /b 1
)
set "JAVA_HOME=%JDK%"
set "PATH=%JDK%\bin;%PATH%"
java -version
