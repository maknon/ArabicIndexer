cd /D %~dp0
jdk\bin\java -Xms1024m -Xmx2048m -cp .;lib/* com.maknoon.OnlineConverter
pause