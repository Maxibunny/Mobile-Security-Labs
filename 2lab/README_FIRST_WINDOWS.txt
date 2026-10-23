LAB #2 - AUTO START / WINDOWS 10-11
=================================

ONE-CLICK START:
  1. Extract the ZIP completely.
  2. Open the 2lab folder (where settings.gradle.kts is located).
  3. Double-click START_LAB2.cmd.
  4. Wait for the summary. Do not close the console while it works.

For a quick re-install and teacher security check WITHOUT re-building:
  Double-click CHECK_LAB2.cmd.

The script looks for Android SDK, builds both APKs, uses a running
emulator or starts an existing AVD, installs both apps with
"adb install --no-streaming", runs the teacher's check.sh (if Git
for Windows is available), runs additional tests and opens both apps.

If it fails, find logs/auto-YYYYMMDD-HHMMSS/summary.txt and send it
with the failing step's *.stdout.txt and *.stderr.txt files.

It does not delete your app data, wipe the emulator, or uninstall apps.
It does not silently accept SDK licenses or install missing system images.
You need Android Studio + SDK, and at least one created virtual phone.
Git for Windows + SDK Command-line Tools are needed for check.sh.

Full Russian instructions: docs/AUTO_WINDOWS.md
README (Russian): README.md

2026-10-21: fixed Windows runner
If you saw "BUILD SUCCESSFUL" and then "gradle_build: exit code" with no number,
the old launcher misread the exit status. This release fixes the launcher.
Run SELFTEST_LAB2.cmd first (no emulator required), then START_LAB2.cmd.
For reinstall/check without rebuild: CHECK_LAB2.cmd.

2026-10-23: STUDY / DEFENSE EDITION
Read docs/00_START_HERE.md first. Full guided explanation:
  docs/CODE_GUIDE.md
  docs/SECURITY_WALKTHROUGH.md
  docs/TEST_PLAN.md
  docs/DEFENSE_CHEATSHEET.md
  docs/GLOSSARY.md
The teacher script scripts/check.sh was preserved without modification.
