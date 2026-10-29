# hype.xbox

Use your Android phone as an Xbox 360 controller for PC games (e.g. Don Bradman Cricket 14) over Wi-Fi.

- `app/` is the Android controller app (Jetpack Compose, landscape, multi-touch).
- `windows/HypeXboxServer/` is the .NET 8 Windows receiver. It creates a virtual Xbox 360 pad through ViGEmBus.

## Setup (PC)
1. Install the **ViGEmBus** driver once: https://github.com/nefarius/ViGEmBus/releases (reboot if asked).
2. Build and run the server:
   ```
   cd windows/HypeXboxServer
   dotnet publish -c Release -r win-x64 --self-contained false -p:PublishSingleFile=true
   ```
   Run `bin/Release/net8.0-windows/win-x64/publish/HypeXboxServer.exe`.
3. Allow it through Windows Firewall on **Private** networks (UDP 39500 and 39501). Make sure the Wi-Fi network profile is set to Private.

## Setup (phone)
1. Build and install the app from Android Studio.
2. Put the phone and PC on the same Wi-Fi (a phone hotspot also works).
3. Open the app and tap your PC in the list, or type the IP shown in the server window.
4. Start the game. It sees a normal wired Xbox 360 controller.

## Controls
- Sticks are floating: touch anywhere in the stick zone and drag. Touch again quickly after releasing to hold L3/R3.
- LT/RT are analog: a press is full, and dragging upward eases the trigger off.
- Tap the green status chip at the top to disconnect.

## Protocol
UDP, little-endian. State port 39500 (20-byte state packets, `HX` magic). Discovery port 39501. Rumble comes back to the phone on the state socket. See `app/.../net/Protocol.kt` and `windows/HypeXboxServer/Protocol.cs`.

## Testing
Open `joy.cpl` on the PC. "Controller (XBOX 360 For Windows)" should appear and react to the phone.
