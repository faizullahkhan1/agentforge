# Switches ONLY the current PowerShell window to Java 21. Your system default (Java 8) is untouched.
# Usage (note the leading dot + space):   . .\use-java21.ps1
# Set JDK21_HOME if your JDK 21 lives somewhere else.

$jdk = if ($env:JDK21_HOME) { $env:JDK21_HOME } else { "$env:USERPROFILE\.jdks\temurin-21.0.7" }

if (-not (Test-Path "$jdk\bin\java.exe")) {
    Write-Error "JDK 21 not found at $jdk. Set JDK21_HOME to your JDK 21 folder."
    return
}

$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"
java -version
