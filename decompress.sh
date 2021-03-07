#!/bin/bash

archs="64"

# [ "$(uname -a)" = "x86_64" ] will work as well but 32 bit response will be i386, i686, i86pc ...
if [ "$(getconf LONG_BIT)" != "$archs" ]; then
	echo "This version of Maknoon Manuscripts Indexer is not for this platform $(uname -m)"
	exit 1
fi

if [ "$(uname)" != "Linux" ]; then
	echo "This version of Maknoon Manuscripts Indexer is not for this platform $(uname)"
	exit 1
fi

ARCHIVE=`awk '/^__ARCHIVE_BELOW__/ {print NR + 1; exit 0; }' $0`

mkdir ArabicIndexer
cd ArabicIndexer || exit
CDIR=`pwd`
tail -n+$ARCHIVE ../$0 | tar -xzmo

mkdir temp pdf

while :
do
	echo "Select the language of the program?"
	echo "(Arabic, English, Urdu) [Arabic] "
	read LANGUAGE
	
	if [ "$LANGUAGE" = "" ] || [ "$LANGUAGE" = "Arabic" ]; then
		PROGRAM_NAME="مفهرس المخطوطات"
		BOOK_FORMAT_DESCRIPTION="ملف بيانات مفهرس المخطوطات"
		printf "Arabic" > setting/setting.txt # printf instead of 'echo -e' because it is not working in ubuntu 10.10, check: https://wiki.ubuntu.com/DashAsBinSh
		break
	else
		if [ "$LANGUAGE" = "English" ]; then
			PROGRAM_NAME="Manuscripts Indexer"
			BOOK_FORMAT_DESCRIPTION="Books Indexer Update File"
			printf "English" > setting/setting.txt
			break
		else
			if [ "$LANGUAGE" = "Urdu" ]; then # TODO: this should be translated
				PROGRAM_NAME="Manuscripts Indexer"
				BOOK_FORMAT_DESCRIPTION="Books Indexer Update File"
				printf "Urdu" > setting/setting.txt
				break
			else
				echo -n "You did not provide a correct input"
			fi
		fi
	fi
done

echo "[Desktop Entry]
Comment=برنامج مفهرس المخطوطات لتصانيف أهل العلم من أهل السنة والجماعة
Exec=\"$CDIR/launcher.sh\" %F
Name=$PROGRAM_NAME
Icon=$CDIR/images/icon_128.png
MimeType=application/biuf
Terminal=false
Type=Application
Path=$CDIR" > maknoon-arabicindexer.desktop

xdg-desktop-menu install maknoon-arabicindexer.desktop

if [ "$(id -u)" != "0" ]; then
	xdg-desktop-icon install maknoon-arabicindexer.desktop
fi

echo "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<mime-info xmlns=\"http://www.freedesktop.org/standards/shared-mime-info\">
	<mime-type type=\"application/biuf\">
		<comment>$BOOK_FORMAT_DESCRIPTION</comment>
		<magic priority=\"50\">
			<match type=\"string\" offset=\"0\" value=\"PK\"/>
		</magic>
		<glob pattern=\"*.biuf\"/>
	</mime-type>
</mime-info>" > maknoon-biuf.xml

xdg-mime install maknoon-biuf.xml

xdg-icon-resource install --context mimetypes --size 64 images/linux_biuf.png application-biuf

xdg-icon-resource install --context apps --size 16 images/icon.png application-arabicindexer
xdg-icon-resource install --context apps --size 32 images/icon_32.png application-arabicindexer
xdg-icon-resource install --context apps --size 64 images/icon_64.png application-arabicindexer
xdg-icon-resource install --context apps --size 128 images/icon_128.png application-arabicindexer

# To prevent any security issue with launcher
if [ "$(id -u)" = "0" ]; then
	chmod -R 777 ../ArabicIndexer # 777 important since the user needs to edit the files
else
	chmod -R u+rwx ../ArabicIndexer
	chmod u+rwx ~/Desktop/maknoon-arabicindexer.desktop
fi

echo "Installation is completed. Please right click on Desktop file and select 'Allow Launching'"
exit 0
__ARCHIVE_BELOW__
