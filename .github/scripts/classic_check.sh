#!/bin/bash
# ServeStream Classic: install, check the names, open it, make sure it stays up, take pictures.
set -u
PKG=net.sourceforge.servestream.classic
APK=$(find apk -name '*.apk' | head -1)
adb install -r -g "$APK"
adb shell settings put global hidden_api_policy 1 >/dev/null 2>&1 || true
RESULT=PASS
adb shell pm list packages | grep -q "package:$PKG$" || { echo "CHECK package installed: FAIL"; RESULT=FAIL; }
adb logcat -c
adb shell am start -W -n $PKG/net.sourceforge.servestream.activity.MainActivity
sleep 12
# a first-run dialog may cover the list: take a picture anyway
adb shell screencap -p /sdcard/classic_main.png
pid=$(adb shell pidof $PKG | tr -d '\r')
if [ -z "$pid" ]; then echo "CHECK app still running after start: FAIL"; RESULT=FAIL; adb logcat -d | grep -E "AndroidRuntime|FATAL" -A20 | head -60; else echo "CHECK app still running after start: PASS"; fi
# the title bar says ServeStream (not "ServeStream Classic")
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
if adb shell cat /sdcard/ui.xml | grep -q 'text="ServeStream"'; then echo "CHECK screen title ServeStream: PASS"; else echo "CHECK screen title ServeStream: FAIL"; RESULT=FAIL; fi
adb shell am start -W -a android.intent.action.VIEW -d "https://stream1.rcast.net/73730" -t audio/mpeg -n $PKG/net.sourceforge.servestream.activity.MainActivity
sleep 15
adb shell screencap -p /sdcard/classic_player.png
adb shell uiautomator dump /sdcard/ui2.xml >/dev/null 2>&1
if adb shell cat /sdcard/ui2.xml | grep -q "Opening URL"; then echo "CHECK Opening URL box closed: FAIL"; RESULT=FAIL; else echo "CHECK Opening URL box closed: PASS"; fi
echo "--- resumed after playing:"; adb shell dumpsys activity activities | grep -E "ResumedActivity|topResumed" | head -3
echo "--- crashes:"; adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|Process: net.sourceforge|ActivityTaskManager.*(START|Displayed)|PLAYBACK" | head -60
pid=$(adb shell pidof $PKG | tr -d '\r')
top=$(adb shell dumpsys activity activities | grep -m1 "topResumedActivity")
if echo "$top" | grep -q "$PKG/net.sourceforge.servestream.activity.MediaPlayerActivity" && ! adb logcat -d | grep -q "FATAL EXCEPTION"; then
  echo "CHECK playing a stream opens the player: PASS"
else
  echo "CHECK playing a stream opens the player: FAIL ($top)"; RESULT=FAIL
fi
adb shell am start -W -n $PKG/net.sourceforge.servestream.activity.MainActivity
sleep 2
adb shell input keyevent KEYCODE_MENU
sleep 4
adb shell screencap -p /sdcard/classic_settings.png
for n in classic_main classic_player classic_settings; do
  adb pull /sdcard/$n.png $n.png >/dev/null 2>&1
  echo "=====SHOT $n BEGIN====="; base64 -w 76 $n.png; echo "=====SHOT $n END====="
done
echo "RESULT $RESULT"
[ "$RESULT" = PASS ]
