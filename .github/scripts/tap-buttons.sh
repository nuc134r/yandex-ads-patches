#!/bin/sh
# Taps the first visible element whose text matches one of the given labels, a few times in a row,
# to get through first start screens and permission dialogs. Used by the emulator test workflows.

labels="$1"
rounds="${2:-5}"

i=0
while [ "$i" -lt "$rounds" ]; do
  i=$((i + 1))
  adb shell uiautomator dump /sdcard/tap.xml > /dev/null 2>&1 || { sleep 3; continue; }
  bounds=$(adb shell cat /sdcard/tap.xml | tr '>' '\n' \
    | grep -E "text=\"($labels)\"" | head -n 1 \
    | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')

  if [ -z "$bounds" ]; then
    sleep 3
    continue
  fi

  set -- $bounds
  x=$(( ($1 + $3) / 2 ))
  y=$(( ($2 + $4) / 2 ))
  echo "Tapping at $x,$y"
  adb shell input tap "$x" "$y"
  sleep 5
done
