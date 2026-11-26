#!/bin/bash
# ServeStream Classic: install, check the names, open it, make sure it stays up, take pictures.
set -u

# tap the first view whose attributes match a pattern (from a UI dump)
tap() {
  adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  local b
  b=$(adb shell cat /sdcard/u.xml | tr '>' '\n' | grep -m1 -- "$1" | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')
  [ -z "$b" ] && return 1
  set -- $b
  adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
}
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
# other apps' sound does not stop the radio: no audio focus held (Android 12+ would fade it out)
if adb shell dumpsys audio | sed -n '/Audio Focus stack/,/^$/p' | grep -q "$PKG"; then echo "CHECK holds no audio focus (keeps playing with other apps): FAIL"; RESULT=FAIL; else echo "CHECK holds no audio focus (keeps playing with other apps): PASS"; fi
# keeps playing in the background
adb shell input keyevent KEYCODE_HOME
sleep 20
if adb shell dumpsys activity services $PKG | grep -q "isForeground=true"; then echo "CHECK keeps playing in the background: PASS"; else echo "CHECK keeps playing in the background: FAIL"; RESULT=FAIL; fi
# media buttons (lock screen / Bluetooth) reach the player; no crash
adb shell input keyevent KEYCODE_MEDIA_PAUSE; sleep 2
adb shell input keyevent KEYCODE_MEDIA_PLAY; sleep 3
if adb logcat -d | grep -q "FATAL EXCEPTION"; then echo "CHECK media buttons: FAIL"; RESULT=FAIL; adb logcat -d | grep -A15 "FATAL EXCEPTION" | head -30; else echo "CHECK media buttons: PASS"; fi
adb shell am start -W -n $PKG/net.sourceforge.servestream.activity.MainActivity
sleep 2
# a backup file in ServeStream's format (extra fields such as images are skipped)
cat > /tmp/classic-test.json <<'JSON'
{"backup":{"uri":[{"nickname":"Classic Test Radio","protocol":"https","username":null,"password":null,"hostname":"stream1.rcast.net","port":443,"path":"/73731","query":null,"reference":null,"lastconnect":-1,"favorite":true,"image":"AAAA"}]}}
JSON
adb push /tmp/classic-test.json /sdcard/Download/classic-test.json >/dev/null
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/classic-test.json >/dev/null 2>&1
# settings: no Donate item; backup opens the system file picker
# (the player is on top of the list: back to the list first)
adb shell input keyevent KEYCODE_BACK; sleep 2
tap 'content-desc="More options"'; sleep 2
tap 'text="Settings"'; sleep 3
adb shell screencap -p /sdcard/classic_settings.png
for i in 1 2 3; do adb shell input swipe 540 1800 540 600 300; sleep 1; done
adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
if adb shell cat /sdcard/u.xml | grep -qi 'text="Donate'; then echo "CHECK no Donate item: FAIL"; RESULT=FAIL; else echo "CHECK no Donate item: PASS"; fi
if tap 'text="Backup &amp; restore"'; then
  sleep 2
  tap 'text="Backup to JSON file"'; sleep 4
  top=$(adb shell dumpsys activity activities | grep -m1 "topResumedActivity")
  if echo "$top" | grep -qi "documentsui"; then echo "CHECK backup opens the file picker: PASS"; else echo "CHECK backup opens the file picker: FAIL ($top)"; RESULT=FAIL; fi
  adb shell screencap -p /sdcard/classic_backup.png
  adb shell input keyevent KEYCODE_BACK; sleep 2
  tap 'text="Restore from JSON file"'; sleep 4
  top=$(adb shell dumpsys activity activities | grep -m1 "topResumedActivity")
  if echo "$top" | grep -qi "documentsui"; then echo "CHECK restore opens the file picker: PASS"; else echo "CHECK restore opens the file picker: FAIL ($top)"; RESULT=FAIL; fi
  # pick the test backup (made by ServeStream's format) and restore it
  if ! tap 'text="classic-test.json"'; then
    tap 'content-desc="Show roots"'; sleep 2
    tap 'text="Downloads"'; sleep 3
    tap 'text="classic-test.json"'
  fi
  sleep 4
  adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  if adb shell cat /sdcard/u.xml | grep -q "Restored 1 new"; then echo "CHECK restore a backup file (result shown): PASS"; else echo "CHECK restore a backup file (result shown): FAIL"; RESULT=FAIL; fi
  adb shell screencap -p /sdcard/classic_restore.png
  tap 'text="OK"'; sleep 1
  adb shell input keyevent KEYCODE_BACK; sleep 2
  if adb logcat -d | grep -q "FATAL EXCEPTION"; then echo "CHECK backup screens without crash: FAIL"; RESULT=FAIL; adb logcat -d | grep -A15 "FATAL EXCEPTION" | head -30; else echo "CHECK backup screens without crash: PASS"; fi
else
  echo "CHECK backup & restore item found: FAIL"; RESULT=FAIL
fi
# back with predictive back turned on (no opt-out): the drawer closes first, the app stays
adb shell am start -W -n $PKG/net.sourceforge.servestream.activity.MainActivity >/dev/null; sleep 2
adb logcat -c
if tap 'content-desc="Open navigation drawer"'; then
  sleep 2
  adb shell input keyevent KEYCODE_BACK; sleep 2
  adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  top=$(adb shell dumpsys activity activities | grep -m1 "topResumedActivity")
  if echo "$top" | grep -q "$PKG/net.sourceforge.servestream.activity.MainActivity" && adb shell cat /sdcard/u.xml | grep -q 'content-desc="Open navigation drawer"'; then
    echo "CHECK back closes the drawer: PASS"
  else
    echo "CHECK back closes the drawer: FAIL ($top)"; RESULT=FAIL
  fi
else
  echo "CHECK drawer button found: FAIL"; RESULT=FAIL
fi
# fold / unfold: the screen changes size while the list and the player are open
pid1=$(adb shell pidof $PKG | tr -d '\r')
adb shell wm size 1840x2208; sleep 5
adb shell screencap -p /sdcard/classic_unfolded.png
top=$(adb shell dumpsys activity activities | grep -m1 "topResumedActivity")
if echo "$top" | grep -q "$PKG/net.sourceforge.servestream.activity.MainActivity" && ! adb logcat -d | grep -q "FATAL EXCEPTION"; then echo "CHECK list survives unfolding: PASS"; else echo "CHECK list survives unfolding: FAIL ($top)"; RESULT=FAIL; fi
adb shell am start -W -a android.intent.action.VIEW -d "https://stream1.rcast.net/73730" -t audio/mpeg -n $PKG/net.sourceforge.servestream.activity.MainActivity >/dev/null
sleep 12
adb shell wm size reset; sleep 5
adb shell screencap -p /sdcard/classic_folded_player.png
pid2=$(adb shell pidof $PKG | tr -d '\r')
top=$(adb shell dumpsys activity activities | grep -m1 "topResumedActivity")
if echo "$top" | grep -q "$PKG/net.sourceforge.servestream.activity.MediaPlayerActivity" && [ "$pid1" = "$pid2" ] && ! adb logcat -d | grep -q "FATAL EXCEPTION"; then
  echo "CHECK player survives folding: PASS"
else
  echo "CHECK player survives folding: FAIL ($top, pid $pid1 -> $pid2)"; RESULT=FAIL
  adb logcat -d | grep -A15 "FATAL EXCEPTION" | head -30
fi
if adb shell dumpsys activity services $PKG | grep -q "isForeground=true"; then echo "CHECK still playing after folding: PASS"; else echo "CHECK still playing after folding: FAIL"; RESULT=FAIL; fi
# back from the player to the list, then back at the top of the list leaves the app
adb shell input keyevent KEYCODE_BACK; sleep 2
top=$(adb shell dumpsys activity activities | grep -m1 "topResumedActivity")
if echo "$top" | grep -q "$PKG/net.sourceforge.servestream.activity.MainActivity"; then echo "CHECK back from player goes to list: PASS"; else echo "CHECK back from player goes to list: FAIL ($top)"; RESULT=FAIL; fi
adb shell input keyevent KEYCODE_BACK; sleep 2
top=$(adb shell dumpsys activity activities | grep -m1 "topResumedActivity")
if echo "$top" | grep -q "$PKG/"; then echo "CHECK back on the list leaves the app: FAIL ($top)"; RESULT=FAIL; else echo "CHECK back on the list leaves the app: PASS"; fi
if adb logcat -d | grep -q "FATAL EXCEPTION"; then echo "CHECK back and resize without crash: FAIL"; RESULT=FAIL; adb logcat -d | grep -A15 "FATAL EXCEPTION" | head -30; else echo "CHECK back and resize without crash: PASS"; fi
for n in classic_main classic_player classic_settings classic_backup classic_restore classic_unfolded classic_folded_player; do
  adb pull /sdcard/$n.png $n.png >/dev/null 2>&1
  echo "=====SHOT $n BEGIN====="; base64 -w 76 $n.png; echo "=====SHOT $n END====="
done
echo "RESULT $RESULT"
[ "$RESULT" = PASS ]
