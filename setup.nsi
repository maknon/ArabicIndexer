; For unicode file should be in UTF8 format WITH BOM
Unicode true

!include "MUI2.nsh"
!include "FileAssociation.nsh"
!include "FileFunc.nsh"
!include "x64.nsh"
!insertmacro RefreshShellIcons
!insertmacro un.RefreshShellIcons

;--------------------------------
!define HOME "E:\ArabicIndexer"

;Interface Configuration
!define MUI_ICON "${HOME}\images.nsis\icon_setup.ico"
!define MUI_UNICON "${HOME}\images.nsis\uninstall.ico"

!define MUI_HEADERIMAGE_UNBITMAP_RTL_STRETCH FitControl
!define MUI_HEADERIMAGE_BITMAP_RTL_STRETCH FitControl
!define MUI_HEADERIMAGE_UNBITMAP_STRETCH FitControl
!define MUI_HEADERIMAGE_BITMAP_STRETCH FitControl

!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP "${HOME}\images.nsis\install_ai_en.bmp"
!define MUI_HEADERIMAGE_BITMAP_RTL "${HOME}\images.nsis\install_ai_ar.bmp"
!define MUI_HEADERIMAGE_UNBITMAP "${HOME}\images.nsis\install_ai_en.bmp"
!define MUI_HEADERIMAGE_UNBITMAP_RTL "${HOME}\images.nsis\install_ai_ar.bmp"
!define MUI_WELCOMEFINISHPAGE_BITMAP "${HOME}\images.nsis\nsis.bmp"

!define PROGRAM_NAME "Maknoon Manuscripts Indexer"
!define PROGRAM_NAME_AR "مفهرس المخطوطات"
!define MAKNOON_APPS "برامج مكنون"

OutFile "ArabicIndexerVMx64.exe"

RequestExecutionLevel admin			; To avoid shortcut removal problem [http://nsis.sourceforge.net/Shortcuts_removal_fails_on_Windows_Vista]

; The default installation directory
InstallDir "$PROGRAMFILES64\${PROGRAM_NAME}"

; Registry key to check for directory (so if you install again, it will overwrite the old one automatically)
InstallDirRegKey HKLM "Software\${PROGRAM_NAME}" "Install_Dir"

;--------------------------------
;Pages

!define MUI_WELCOMEPAGE_TITLE "$(WELCOME_TITLE)"
!define MUI_WELCOMEPAGE_TEXT "$(WELCOME_TEXT)"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

;--------------------------------

!insertmacro MUI_LANGUAGE "Arabic"
!insertmacro MUI_LANGUAGE "English"

LangString WELCOME_TEXT ${LANG_ENGLISH} "Al Salam Alaikum$\r$\n\
$\r$\n\
The program is useful to index PDF files, search within these files and displaying the exact pages.$\r$\n\
$\r$\n\
This product is licensed to all Muslims$\r$\n\
Copyright©2021 Maknoon.com$\r$\n\
This product is not for sale$\r$\n\
Version 2.2"
LangString WELCOME_TEXT ${LANG_ARABIC} "السلام عليكم ورحمة الله وبركاته$\r$\n\
$\r$\n\
يقوم برنامج مفهرس المخطوطات بفهرسة كتب PDF، بالإضافة إلى الفهرسة يمكِّن البرنامج المستخدم من البحث خلال الفهرسة وعرض الصفحة المحددة التي تتضمن نتيجة البحث بإحدى المستعرضات.$\r$\n\
مزية البرنامج هو محاولة الجمع بين الثقة في الكتب المصور عن أصولها وبين إمكانية البحث في هذه الكتب مع ترتيبها وتقسيمها.$\r$\n\
$\r$\n\
يمنع بيع البرنامج أو استخدامه فيما يخالف أهل السنة والجماعة$\r$\n\
البرنامج مصرح لنشره واستخدامه من جميع المسلمين$\r$\n\
جميع الحقوق محفوظة لموقع مكنون$\r$\n\
الإصدار 2.2"

LangString WELCOME_TITLE ${LANG_ENGLISH} "${PROGRAM_NAME}"
LangString WELCOME_TITLE ${LANG_ARABIC} "${PROGRAM_NAME_AR}"

LangString BOOK_FORMAT_DESCRIPTION ${LANG_ENGLISH} "Books Indexer Update File"
LangString BOOK_FORMAT_DESCRIPTION ${LANG_ARABIC} "ملف بيانات مفهرس المخطوطات"

LangString PUBLISHER_NAME ${LANG_ENGLISH} "Maknoon Apps"
LangString PUBLISHER_NAME ${LANG_ARABIC} "برامج مكنون"

LangString SHORTCUT_START ${LANG_ENGLISH} "Manuscripts Indexer"
LangString SHORTCUT_START ${LANG_ARABIC} "${PROGRAM_NAME_AR}"

LangString INSTALLER_NAME ${LANG_ENGLISH} "${PROGRAM_NAME}"
LangString INSTALLER_NAME ${LANG_ARABIC} "${PROGRAM_NAME_AR}"

Function .onInit

	ReadRegStr $R0 HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "UninstallString"
	StrCmp $R0 "" cont
	
	ReadRegStr $LANGUAGE HKLM "SOFTWARE\${PROGRAM_NAME}" Lang
	StrCmp $LANGUAGE "1033" true false
	true:
		MessageBox MB_YESNOCANCEL|MB_ICONINFORMATION "${PROGRAM_NAME} is already installed. Do you want to remove the previous version?" IDYES uninst IDCANCEL abort
		Goto done
	false:
		MessageBox MB_YESNOCANCEL|MB_ICONINFORMATION "هناك نسخة أخرى من برنامج مفهرس المخطوطات على جهازك. هل تريد إزالتها؟" IDYES uninst IDCANCEL abort
		Goto done
	
	;Run the uninstaller
	uninst:
		ClearErrors
		ExecWait '$R0 _?=$INSTDIR' ;Do not copy the uninstaller to a temp file
		
		IfErrors no_remove_uninstaller
			;You can either use Delete /REBOOTOK in the uninstaller or add some code
			;here to remove the uninstaller. Use a registry key to check
			;whether the user has chosen to uninstall. If you are using an uninstaller
			;components page, make sure all sections are uninstalled.
		no_remove_uninstaller:
		Goto done
	
	abort:
		Abort
	
	cont:
	
	;Default is Arabic
	WriteRegStr HKLM "SOFTWARE\${PROGRAM_NAME}" "Lang" "1025"
	!define MUI_LANGDLL_REGISTRY_ROOT HKLM
	!define MUI_LANGDLL_REGISTRY_KEY "Software\${PROGRAM_NAME}"
	!define MUI_LANGDLL_REGISTRY_VALUENAME "Lang"
	!define MUI_LANGDLL_ALWAYSSHOW
	
	;Language selection dialog
	!define MUI_LANGDLL_WINDOWTITLE "Program Language لغة البرنامج"
	!define MUI_LANGDLL_INFO "Select the language اختر لغة البرنامج"
	!insertmacro MUI_LANGDLL_DISPLAY
	
	Pop $LANGUAGE
	StrCmp $LANGUAGE "cancel" 0 +2
		Abort
	done:

FunctionEnd

; The name of the installer
Name "$(INSTALLER_NAME)"
BrandingText "$(INSTALLER_NAME)"
VIProductVersion "2.2.0.0"
VIAddVersionKey "ProductName" "${PROGRAM_NAME_AR}"
VIAddVersionKey "CompanyName" "${MAKNOON_APPS}"
VIAddVersionKey "LegalCopyright" "©maknoon.com"
VIAddVersionKey "FileDescription" "${PROGRAM_NAME_AR}"
VIAddVersionKey "FileVersion" "2.2"
VIAddVersionKey "InternalName" "${PROGRAM_NAME}"

; The stuff to install
Section "${PROGRAM_NAME}" SEC_IDX
	
	SectionIn RO
	
	SetOutPath "$INSTDIR\setting"		; Set output path to the installation directory.
	File /r setting\*.*					; Put file there
	
	SetOutPath "$INSTDIR\lib"
	File /r lib\*.*

	SetOutPath "$INSTDIR\jdk"
	File /r jdk\*.*
	
	SetOutPath "$INSTDIR\com"
	File /r com\*.*
	
	SetOutPath "$INSTDIR\images"
	File /r /x linux* images\*.*
	
	SetOutPath "$INSTDIR\language"
	File /r language\*.*
	
	SetOutPath "$INSTDIR\arabicIndex"
	File /r arabicIndex\*.*
	
	SetOutPath "$INSTDIR\arabicLuceneIndex"
	File /r arabicLuceneIndex\*.*
	
	SetOutPath "$INSTDIR\arabicRootsIndex"
	File /r arabicRootsIndex\*.*
	
	SetOutPath "$INSTDIR\arabicRootsTableIndex"
	File /r arabicRootsTableIndex\*.*
	
	SetOutPath "$INSTDIR\englishIndex"
	File /r englishIndex\*.*
	
	SetOutPath "$INSTDIR\db"
	File /r db\*.*
	
	SetOutPath "$INSTDIR\bin"
	File /r bin\*.*
	
	CreateDirectory "$INSTDIR\temp"
	CreateDirectory "$INSTDIR\pdf"
	
	SetOutPath "$INSTDIR"
	File "ArabicIndexer.exe"
	File "ArabicIndexer.jar"
	File "startup.bat"
	File "ArabicIndexer.l4j.ini"
	File "DBDefrag.bat"
	File "DBEmpty.bat"
	File "Launcher.exe"
	File "launcher.bat"
	File "Launcher.jar"
	;File "derby.properties"	; When derby is in use

	SetRegView 64 ; To solve the issue with icons on x64
	${registerExtension} "$INSTDIR\Launcher.exe" ".biuf" "$(BOOK_FORMAT_DESCRIPTION)" 1
	${RefreshShellIcons}
	SetRegView 32
	
	;To handle the language at the beginning
	ClearErrors
	FileOpen $0 "$INSTDIR\setting\setting.txt" w
	IfErrors Done
	StrCmp $LANGUAGE "1033" true false
	true:
		FileWrite $0 "English"
		Goto Done
	false:
		FileWrite $0 "Arabic"
	Done:
	FileClose $0

	; Write the installation path & the language into the registry
	WriteRegStr HKLM "SOFTWARE\${PROGRAM_NAME}" "Install_Dir" "$INSTDIR"
	WriteRegStr HKLM "SOFTWARE\${PROGRAM_NAME}" "Lang" "$LANGUAGE"

	; Write the uninstall keys for Windows
	WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "DisplayName" "$(INSTALLER_NAME)"
	WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "URLInfoAbout" "https://www.maknoon.com"
	WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "DisplayIcon" "$INSTDIR\ArabicIndexer.exe,0"
	WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "DisplayVersion" "2.2"
	WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "Publisher" "$(PUBLISHER_NAME)"
	WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "UninstallString" '"$INSTDIR\uninstall.exe"'
	WriteRegDWORD HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "NoModify" 1
	WriteRegDWORD HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "NoRepair" 1
	WriteUninstaller "$INSTDIR\uninstall.exe"
	
	SectionGetSize ${SEC_IDX} $0
	IntFmt $0 "0x%08X" $0
	WriteRegDWORD HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "EstimatedSize" "$0"
	
	; To allow all users to save setting in Vista/W7 because of UAC
	AccessControl::GrantOnFile "$INSTDIR" "(BU)" "FullAccess"

SectionEnd

Section "Start Menu Shortcuts"
	CreateShortCut "$SMPROGRAMS\$(SHORTCUT_START).lnk" "$INSTDIR\ArabicIndexer.exe"

	; Create desktop shortcut
	CreateShortCut "$DESKTOP\$(SHORTCUT_START).lnk" "$INSTDIR\ArabicIndexer.exe"
SectionEnd

Section "Uninstall"

	; Remove registry keys
	DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}"
	DeleteRegKey HKLM "SOFTWARE\${PROGRAM_NAME}"

	; Remove shortcut/directories used
	Delete "$DESKTOP\$(SHORTCUT_START).lnk"
	Delete "$SMPROGRAMS\$(SHORTCUT_START).lnk"
	RMDir /r "$INSTDIR"

	SetRegView 64 ; To solve the issue with icons on x64
	${unregisterExtension} ".biuf" "$(BOOK_FORMAT_DESCRIPTION)"
	${un.RefreshShellIcons}
	SetRegView 32
	
SectionEnd

Function un.onInit
	ReadRegStr $LANGUAGE HKLM "SOFTWARE\${PROGRAM_NAME}" Lang
FunctionEnd