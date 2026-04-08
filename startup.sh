#!/bin/sh

cd "$(dirname "${0}")" || exit 1
jdk/bin/java -Dfile.encoding=windows-1256 -Xms64m -Xmx2048m --add-modules jdk.incubator.vector -jar ArabicIndexer.jar "$@"
