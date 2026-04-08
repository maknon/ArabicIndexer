#!/bin/sh

open -a Preview "$1"

osascript <<EOF
  tell application "Preview" to activate
  tell application "System Events" to tell process "Preview" to click menu item "Go to Page…" of menu "Go" of menu bar 1
  tell application "System Events" to keystroke "$2"
  tell application "System Events" to key code 36
EOF