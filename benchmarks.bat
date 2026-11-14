@echo off
setlocal
cd /d "%~dp0"
call mvn -Pjmh -q clean package -DskipTests -f "%~dp0pom.xml"
if errorlevel 1 exit /b 1
java -jar "%~dp0target\benchmarks.jar" %*
