#!/usr/bin/env bash
set -euo pipefail

APK="clean-app/app/build/outputs/apk/debug/app-debug.apk"
test -f "$APK"
adb install -r "$APK"
adb logcat -c
adb shell am force-stop com.planify.app || true
adb shell monkey -p com.planify.app -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 4

cat > /tmp/tap_text.py <<'PY'
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

text = sys.argv[1]
subprocess.run(['adb', 'shell', 'uiautomator', 'dump', '/sdcard/window.xml'], check=True, stdout=subprocess.DEVNULL)
subprocess.run(['adb', 'pull', '/sdcard/window.xml', '/tmp/window.xml'], check=True, stdout=subprocess.DEVNULL)
root = ET.parse('/tmp/window.xml').getroot()
for n in root.iter('node'):
    if n.attrib.get('text') == text or n.attrib.get('content-desc') == text:
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds', ''))
        if m:
            x = (int(m.group(1)) + int(m.group(3))) // 2
            y = (int(m.group(2)) + int(m.group(4))) // 2
            subprocess.run(['adb', 'shell', 'input', 'tap', str(x), str(y)], check=True)
            print(f'tapped {text} at {x},{y}')
            sys.exit(0)
print(f'NOT FOUND: {text}')
sys.exit(2)
PY

python3 /tmp/tap_text.py '＋'
sleep 1
python3 /tmp/tap_text.py 'Clase'
sleep 2

if ! adb shell pidof com.planify.app >/dev/null; then
  echo 'APP DIED AFTER OPENING EDITOR'
  adb logcat -d -v threadtime '*:E'
  exit 20
fi

adb shell uiautomator dump /sdcard/editor.xml >/dev/null
adb pull /sdcard/editor.xml /tmp/editor.xml >/dev/null
python3 - <<'PY'
import re
import subprocess
import xml.etree.ElementTree as ET

root = ET.parse('/tmp/editor.xml').getroot()
edits = [n for n in root.iter('node') if n.attrib.get('class') == 'android.widget.EditText']
if not edits:
    raise SystemExit('No EditText found')
n = edits[0]
m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib['bounds'])
if not m:
    raise SystemExit('Invalid EditText bounds')
x = (int(m.group(1)) + int(m.group(3))) // 2
y = (int(m.group(2)) + int(m.group(4))) // 2
subprocess.run(['adb', 'shell', 'input', 'tap', str(x), str(y)], check=True)
subprocess.run(['adb', 'shell', 'input', 'text', 'PruebaPlanify'], check=True)
PY
sleep 1
python3 /tmp/tap_text.py 'Guardar' || {
  adb shell input keyevent 111
  sleep 1
  python3 /tmp/tap_text.py 'Guardar'
}
sleep 3

if ! adb shell pidof com.planify.app >/dev/null; then
  adb logcat -d -v threadtime | grep -E 'FATAL EXCEPTION|AndroidRuntime|Process: com\.planify\.app|am_crash' | tail -n 250 || true
  exit 21
fi

python3 /tmp/tap_text.py 'Ajustes'
sleep 2
if ! adb shell pidof com.planify.app >/dev/null; then
  adb logcat -d -v threadtime '*:E' | tail -n 200
  exit 22
fi

echo 'Runtime smoke passed'
