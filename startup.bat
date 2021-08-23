cd /D %~dp0
REM -XX:PermSize=256m -XX:MaxPermSize=256m
jdk\bin\java -Dfile.encoding=windows-1256 -Xms64m -Xmx2048m -jar ArabicIndexer.jar %*
pause