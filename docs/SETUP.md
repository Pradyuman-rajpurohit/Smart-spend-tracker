# Setting up with Android Studio

You only need to do steps 1 and 2 once.

## 1. Open the project

1. Start Android Studio.
2. Pick **File > Open** (or **Open** on the welcome screen).
3. Choose the project folder (the one containing `settings.gradle.kts`) and press **OK**.
4. If it asks whether to trust the project, choose **Trust Project**.
5. Wait. A progress bar at the bottom says *Gradle sync* or *Indexing*. The first time this
   takes a few minutes. It is done when the bar disappears and the **Run** button (green
   triangle at the top) turns green.

## 2. Prepare the phone

1. On the phone open **Settings > About phone** and tap **Build number** seven times.
   A message says you are now a developer.
2. Open **Settings > System > Developer options** (on Samsung it is directly under Settings)
   and switch on **USB debugging**.
3. Connect the phone to the PC with a USB cable. When the phone shows
   **Allow USB debugging?**, tick **Always allow from this computer** and tap **Allow**.
4. If the phone does not appear in Android Studio, change the USB mode on the phone from
   *Charging only* to *File transfer*.

## 3. Run it

1. In the toolbar at the top of Android Studio there is a device dropdown next to the green
   triangle. It should now show your phone's name. If it shows *No devices*, unplug and
   replug the cable.
2. Press the green triangle (**Run**).
3. The first build takes two to five minutes. After that the app opens on the phone by
   itself and stays installed like any other app.

Every time you want a newer version after a change, plug the phone in and press the green
triangle again. Your data is kept between versions.

## Faster builds

The green triangle installs the *debug* build, which carries extra checks and is much
slower, especially on the emulator. For everyday use install the *release* build instead:

1. Choose **Build > Select Build Variant**.
2. In the row for `app`, change **debug** to **release**.
3. Press the green triangle as usual. The release build is about ten times smaller and
   starts in a fraction of the time. It is signed with the same key, so it installs over
   the debug one and keeps your data.

Switch the variant back to **debug** only if you want Android Studio's debugger.

## Without a cable

1. Choose **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
2. When the small notification appears, click **locate**.
3. Send that file to the phone (cloud drive, a message to yourself, or a cable as a file).
4. Open it on the phone and allow installing from that app when asked.

## Testing SMS capture on the emulator

The emulator has no SIM, but it can receive fake messages. With the emulator running:

```bash
adb emu sms send VM-SBIUPI "Dear UPI user A/C X1234 debited by 486.0 on date 28Sep26 trf to SWIGGY Refno 527112345678. -SBI"
```

Switch SMS capture on under Profile first. The transaction appears within a second and a
notification opens it for review.

## If Android Studio complains

- **"Gradle JDK" or "Java" errors**: open **File > Settings > Build, Execution, Deployment >
  Build Tools > Gradle** and make sure **Gradle JDK** is set to the *Embedded JDK*. Press OK
  and choose **File > Sync Project with Gradle Files**.
- **SDK missing**: accept the prompt that offers to install the missing SDK component.
- **Phone not detected**: check USB debugging is on, try another cable or another USB port.

## Command line

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug     # tests + debug APK
./gradlew :app:assembleRelease                          # release APK
```

Run the two `assemble` tasks in separate invocations; both export the Room schema and can
step on each other when run together. `local.properties` (the SDK path) is machine-specific;
Android Studio writes it on first open.
