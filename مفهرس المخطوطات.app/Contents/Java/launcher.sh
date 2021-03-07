#!/bin/sh

cd "$(dirname "${0}")" || exit 1

../PlugIns/jdk/bin/java \
	-Dapple.awt.UIElement=true \
	-jar Launcher.jar "$@"
