#!/bin/bash
# Emulator test: launches the app, plays a real radio stream with the FFmpeg
# player, checks that playback survives going to the background, rotation and
# foldable-like screen sizes, and prints screenshots (base64) and logs.
APK=$(find apk -name '*.apk' | head -1)
PKG=net.sourceforge.servestream
STREAM="https://stream1.rcast.net/73730"
T="timeout 60"
RESULTS=""

shot() {
  sleep "${2:-5}"
  $T adb exec-out screencap -p > "/tmp/$1.png"
  echo "=====SHOT $1 BEGIN====="
  # split into short lines: very long log lines get cut off by GitHub
  base64 -w 3000 "/tmp/$1.png"
  echo "=====SHOT $1 END====="
}

uid_of_app() { $T adb shell cmd package list packages -U $PKG | sed -n 's/.*uid:\([0-9]*\).*/\1/p' | head -1; }

# 1 = our app has an AudioTrack in "started" state, 0 otherwise
audio_playing() {
  local uid; uid=$(uid_of_app)
  $T adb shell dumpsys audio | grep -E "u/pid:$uid/" | grep -q "state:started" && echo 1 || echo 0
}

# 1 = the player screen shows a non-empty station/track name (or no player on screen)
info_shown() {
  $T adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local xml; xml=$($T adb shell cat /sdcard/ui.xml)
  if ! echo "$xml" | grep -q 'id/trackname'; then echo 1; return; fi
  echo "$xml" | grep -oE 'text="[^"]*" resource-id="net.sourceforge.servestream:id/trackname"' | grep -qv 'text=""' && echo 1 || echo 0
}

check() {
  local name="$1"
  local alive=0; $T adb shell pidof $PKG >/dev/null && alive=1
  local playing; playing=$(audio_playing)
  local info; info=$(info_shown)
  local res="FAIL"; [ "$alive" = 1 ] && [ "$playing" = 1 ] && [ "$info" = 1 ] && res="PASS"
  echo "CHECK $name: alive=$alive audio_started=$playing info_shown=$info -> $res"
  RESULTS="$RESULTS\n$res  $name"
}

echo "boot done: $($T adb shell getprop sys.boot_completed), sdk $($T adb shell getprop ro.build.version.sdk)"
$T adb shell wm size; $T adb shell wm density
$T adb install -r -g "$APK" || exit 1
$T adb shell appops set $PKG POST_NOTIFICATION allow
$T adb logcat -c

# --- phone portrait, station list
$T adb shell am start -n $PKG/.activity.MainActivity
shot phone_main 8

# --- play a real stream (FFmpeg + TLS)
$T adb shell am start -a android.intent.action.VIEW -d "$STREAM" -t audio/mpeg -n $PKG/.activity.MainActivity
sleep 25
shot phone_player 2
check "stream plays"

# --- background: go home, wait
$T adb shell input keyevent KEYCODE_HOME
sleep 30
check "keeps playing in background (30 s)"

# --- rotation
$T adb shell am start -n $PKG/.activity.MainActivity
$T adb shell settings put system accelerometer_rotation 0
$T adb shell settings put system user_rotation 1
shot phone_landscape 6
check "keeps playing after rotation"
$T adb shell settings put system user_rotation 0

# --- foldable-like sizes (density 420)
$T adb shell wm density 420
for size in "1840x1840 fold_open_1x1 1" "1800x2400 fold_open_4x3 1" "1080x1728 fold_closed_10x16 0"; do
  set -- $size
  $T adb shell wm size "$1"
  shot "$2" 7
  check "keeps playing at $2 ($1)"
  pane=0
  $T adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  $T adb shell cat /sdcard/ui.xml | grep -q 'id/station_list_pane' && pane=1
  res="FAIL"; [ "$pane" = "$3" ] && res="PASS"
  echo "CHECK two-pane layout at $2: station_list_pane=$pane expected=$3 -> $res"
  RESULTS="$RESULTS\n$res  two-pane layout at $2"
done
$T adb shell wm size reset
$T adb shell wm density reset

# --- Tier 3: multi-window support (a phone emulator cannot be forced into split screen,
# so check the system treats the app as resizeable, i.e. allowed in multi-window)
rm=$($T adb shell dumpsys activity activities | grep -A60 "$PKG/" | grep -o "resizeMode=[A-Z_]*" | head -1)
echo "multi-window: $rm"
res="FAIL"; case "$rm" in *UNRESIZEABLE*|"") ;; *RESIZEABLE*) res="PASS" ;; esac
echo "CHECK multi-window support ($rm): -> $res"
RESULTS="$RESULTS\n$res  multi-window support ($rm)"

# --- Android Auto: connect like the car does (media browser), list stations, play
TESTAPK=$(find testapk -name '*.apk' | head -1)
$T adb install -r -g "$TESTAPK"
for t in "a_stationListShowsSavedStationsWithImages Android Auto: station list with images" \
         "b_playFromStationList Android Auto: play a station from the list" \
         "c_playFromVoiceSearch Android Auto: play by voice search" \
         "d_skipButtonsSwitchStations Android Auto: skip buttons switch saved stations" \
         "BackupImageTest#imageComesBackAfterRemovingIt Backup: image restored after removing it" \
         "BackupImageTest#imageComesBackWithDeletedStation Backup: deleted station restored with image" \
         "BackupImageTest#imageComesBackWhenALeftoverFileExists Backup: image restored over a leftover file" \
         "BackupImageTest#leftoverPicturesAreCleanedUp Backup: leftover pictures cleaned up" \
         "BackupImageTest#tenStationsAllImagesComeBack Backup: ten stations, all images restored" \
         "ClassicThemeTest#classicThemeIsApplied Theme: classic look applied" \
         "ClassicThemeTest#switchingBackRestoresTheNormalLook Theme: switching back from classic" \
         "ClassicThemeTest#platinumThemeIsApplied Theme: platinum look applied" \
         "ClassicThemeTest#homeScreenIconFollowsTheTheme Theme: home screen icon follows the theme" \
         "ClassicThemeTest#metroDarkThemeIsApplied Theme: metro dark look applied (colours, switches, drop-downs)" \
         "ClassicThemeTest#metroLightThemeIsApplied Theme: metro light look applied (colours, switches, drop-downs)" \
         "ClassicThemeTest#metroHomeTiles Metro: tile home, stations tile, back returns home" \
         "ClassicThemeTest#metroStationTabsAndFavorites Metro: all / favourites / recent tabs, star, backup keeps it" \
         "ClassicThemeTest#metroPlayer Metro: player (picture with level bars, name, round play button)" \
         "ClassicThemeTest#realThemeIsApplied Theme: real look applied (status display, fields)" \
         "ClassicThemeTest#realPlayerOnUnfoldedScreen Theme: real player on unfolded screen (picture above fields)" \
         "FixesTest#themeChangeDoesNotCloseTheApp Theme change keeps the app open (icon switched after leaving)" \
         "FixesTest#bluetoothOptionsWithoutPermission Bluetooth options without permission: no crash" \
         "Tier3Test#listScrollPositionSurvivesRecreation Tier 3: list scroll position kept" \
         "Tier3Test#typedTextSurvivesRecreation Tier 3: typed text kept"; do
  set -- $t; m=$1; shift
  case "$m" in *#*) ;; *) m="AutoBrowseTest#$m" ;; esac
  case "$m" in *bluetoothOptions*) $T adb shell pm revoke $PKG android.permission.BLUETOOTH_CONNECT ;; esac
  out=$(timeout 180 adb shell am instrument -w -e class net.sourceforge.servestream.$m \
        $PKG.test/androidx.test.runner.AndroidJUnitRunner 2>&1)
  echo "$out" | tail -n 30
  res="FAIL"; echo "$out" | grep -q "^OK (1 test)" && res="PASS"
  echo "CHECK $*: -> $res"
  RESULTS="$RESULTS\n$res  $*"
done

$T adb shell pm grant $PKG android.permission.BLUETOOTH_CONNECT
for n in classic_main classic_settings classic_player platinum_main platinum_settings platinum_player metro_dark_main metro_dark_settings metro_light_main metro_light_settings metro_dark_home metro_light_home metro_dark_list metro_dark_favorites metro_light_list metro_light_favorites metro_dark_player metro_light_player real_main real_settings real_player real_player_unfolded; do
  $T adb exec-out cat /sdcard/Download/$n.png > /tmp/$n.png 2>/dev/null
  echo "=====SHOT $n BEGIN====="; base64 -w 3000 /tmp/$n.png; echo "=====SHOT $n END====="
done

# --- app language: 조선말 (ko-KP) shows its own wording ("차림표 열기" = open menu)
$T adb shell cmd locale set-app-locales $PKG --locales ko-KP
$T adb shell am force-stop $PKG
$T adb shell am start -n $PKG/.activity.MainActivity
sleep 6
$T adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
kp=0; $T adb shell cat /sdcard/ui.xml | grep -q '차림표 열기' && kp=1
shot language_ko_kp 1
res="FAIL"; [ "$kp" = 1 ] && res="PASS"
echo "CHECK language ko-KP: -> $res"
RESULTS="$RESULTS\n$res  language: 조선말 (ko-KP) strings shown"
$T adb shell cmd locale set-app-locales $PKG --locales ""

# --- media button after the playback service has shut down (crash in 2.6.4 and earlier)
$T adb shell input keyevent KEYCODE_MEDIA_PAUSE
sleep 2
$T adb shell am start -n $PKG/.activity.MainActivity
sleep 3
for k in 1 2 3 4; do $T adb shell input keyevent KEYCODE_BACK; sleep 1; done
$T adb shell am stopservice -n $PKG/.service.MediaPlaybackService
sleep 6
svc=$($T adb shell dumpsys activity services $PKG | grep -c "MediaPlaybackService")
echo "playback service records after stop: $svc"
$T adb logcat -c
$T adb shell input keyevent KEYCODE_MEDIA_PLAY
sleep 6
crash=$($T adb logcat -d | grep -c "FATAL EXCEPTION")
$T adb logcat -d | grep -A12 "FATAL EXCEPTION" | head -30
res="FAIL"; [ "$crash" = 0 ] && res="PASS"
echo "CHECK late media button: crashes=$crash -> $res"
RESULTS="$RESULTS\n$res  media button after service shutdown: no crash"
$T adb shell input keyevent KEYCODE_MEDIA_PAUSE

echo "=====LOGCAT BEGIN====="
$T adb logcat -d -v brief | grep -iE "servestream|FFmpeg|AndroidRuntime|FATAL|TAG  |MediaPlayer" | grep -viE "ApkAssets|PackageConfigPersister" | tail -n 200
echo "=====LOGCAT END====="

echo "=====RESULTS BEGIN====="
echo -e "$RESULTS"
echo "=====RESULTS END====="
exit 0
