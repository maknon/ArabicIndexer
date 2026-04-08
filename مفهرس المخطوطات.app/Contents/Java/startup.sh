#!/bin/sh

cd "$(dirname "${0}")" || exit 1

../PlugIns/jdk/bin/java \
	-Xms64m \
	-Xmx512m \
	-Dapple.laf.useScreenMenuBar=true \
	-Xdock:name="المفهرس" \
	-Xdock:icon=../Resources/icon.icns \
	--add-modules jdk.incubator.vector \
	-jar ArabicIndexer.jar "$@"
