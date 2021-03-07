#!/bin/sh

cd "$(dirname "${0}")" || exit 1

jdk/bin/java -jar Launcher.jar "$@"
