# hype.xbox

Turn your Android phone into an Xbox controller for your PC. Play any game that supports an Xbox 360 controller, wirelessly over Wi-Fi.

## What you need
- A Windows 10 or 11 PC
- An Android phone (Android 9 or newer)
- Both on the same Wi-Fi (a phone hotspot works too)

## Get started

### Step 1: Set up your PC (once)
1. **Install the controller driver.** Download and run the latest ViGEmBus installer from the [ViGEmBus releases page](https://github.com/nefarius/ViGEmBus/releases), then restart your PC if it asks.
2. **Download the PC app.** Get `hype.xbox.exe` from the [Releases page](../../releases/latest) and save it anywhere, such as your Desktop.
3. **Open it.** If Windows asks you to install .NET 8 Desktop Runtime, accept. If it asks about the firewall, tick **Private networks** and click **Allow**.

### Step 2: Set up your phone (once)
1. On your phone, open the [Releases page](../../releases/latest) and download the **APK** file.
2. Open the file to install it. If Android asks, allow installing from your browser or file manager.

### Step 3: Play
1. Open **hype.xbox** on your PC and leave it running.
2. Open the **hype.xbox** app on your phone. It looks for your PC by itself.
3. Tap your PC's name in the list. Once the status at the top turns green, you're connected.
4. Start your game. It will treat your phone as a normal Xbox controller.

If your PC doesn't show up, tap the refresh button, or type the IP address shown in the PC app into **Manual connection** and tap **Connect**.

To disconnect, tap the green status chip at the top of the phone screen.

## Controls
- **Sticks:** touch anywhere in the stick area and drag. Tap again quickly after letting go to press the stick in (L3/R3).
- **Triggers (LT / RT):** press for full pull. Slide your finger upward to ease off.
- **Buttons, D-pad and bumpers:** work like a regular Xbox controller.
- **Vibration:** when the game rumbles, your phone does too.

## Not working?
- **PC isn't listed:** make sure both devices are on the same Wi-Fi. On the PC, set your Wi-Fi network to **Private** (Settings > Network & internet > Wi-Fi > your network). Public or guest Wi-Fi often blocks this, so try a phone hotspot. You can always connect by typing the IP address.
- **PC app says the driver is missing:** install ViGEmBus (Step 1) and restart your PC.
- **Game doesn't see the controller:** check the PC app is running and the phone says connected. To test, press `Win + R`, type `joy.cpl` and press Enter. You should see "Controller (XBOX 360 For Windows)" move when you touch the phone.
- **Buttons stuck after losing Wi-Fi:** the PC app releases everything automatically after a second.

---

## For developers
- `app/` is the Android app (Kotlin, Jetpack Compose). Open the repo root in Android Studio to build it.
- `windows/hype.xbox/` is the Windows receiver (.NET 8, WinForms, ViGEm). Build it with:
  ```
  cd windows/hype.xbox
  dotnet publish -c Release -r win-x64 --self-contained false -p:PublishSingleFile=true
  ```
- Protocol: UDP, little-endian. State on port 39500 (20-byte packets, `HX` magic), discovery on 39501, rumble returned on the state socket. See `app/src/main/java/com/xbox/hype/net/Protocol.kt` and `windows/hype.xbox/Protocol.cs`.
