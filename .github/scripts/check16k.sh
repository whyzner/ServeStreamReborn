#!/bin/bash
# Fails if any native library or the APK itself is not aligned for 16 KB pages.
set -u
APK=$(find app/build/outputs/apk/debug -name '*.apk' | head -1)
NDK="$ANDROID_HOME/ndk/27.2.12479018"
READELF="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
ZIPALIGN=$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -1)zipalign
fail=0
work=$(mktemp -d)
unzip -q -o "$APK" 'lib/*' -d "$work"
echo "APK: $APK"
for so in $(find "$work/lib" -name '*.so' | sort); do
  rel=${so#$work/}
  # smallest p_align among PT_LOAD segments
  min=$("$READELF" -lW "$so" | awk '$1=="LOAD"{a=strtonum($NF); if(m==""||a<m)m=a} END{print m+0}')
  if [ "$min" -ge 16384 ]; then
    printf '  OK   %-45s LOAD align 0x%x\n' "$rel" "$min"
  else
    printf '  FAIL %-45s LOAD align 0x%x (< 0x4000)\n' "$rel" "$min"; fail=1
  fi
done
if "$ZIPALIGN" -c -P 16 -v 4 "$APK" > "$work/zipalign.txt" 2>&1; then
  echo "  OK   APK zip alignment (16 KB for uncompressed .so)"
else
  echo "  FAIL APK zip alignment"; grep -i "lib/" "$work/zipalign.txt" | grep -iv "OK" | head -20; fail=1
fi
echo "ABIs: $(ls "$work/lib" | tr '\n' ' ')"
exit $fail
