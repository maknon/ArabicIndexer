#!/bin/sh

cd "$(dirname "${0}")" || exit 1
jdk/bin/java -Dfile.encoding=windows-1256 -Xms64m -Xmx512m -jar ArabicIndexer.jar "$@"
