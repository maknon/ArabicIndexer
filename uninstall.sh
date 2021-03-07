#!/bin/sh
cd "$(dirname "${0}")"

if [ "$(id -u)" != "0" ]; then
	# Check if the folder is created by root user by testing oner file inside it. you can use 'find ArabicIndexer.jar -user root' as well
	if [ "$(ls -l | awk '{if ($8 == "ArabicIndexer.jar" && $3 == "root") print $8}')" = "ArabicIndexer.jar" ]; then
		echo "You cannot uninstall Maknoon Manuscripts Indexer since it is created by root user."
		exit 0
	fi
fi

xdg-desktop-menu uninstall maknoon-arabicindexer.desktop

if [ "$(id -u)" != "0" ]; then
	xdg-desktop-icon uninstall maknoon-arabicindexer.desktop
fi

xdg-mime uninstall maknoon-biuf.xml

xdg-icon-resource uninstall --context mimetypes --size 64 application-biuf

xdg-icon-resource uninstall --context apps --size 16 maknoon-arabicindexer
xdg-icon-resource uninstall --context apps --size 32 maknoon-arabicindexer
xdg-icon-resource uninstall --context apps --size 64 maknoon-arabicindexer
xdg-icon-resource uninstall --context apps --size 128 maknoon-arabicindexer

cd ..; rm -rf ArabicIndexer