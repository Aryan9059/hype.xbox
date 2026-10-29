using System.Buffers.Binary;

namespace HypeXboxServer;

/// <summary>Wire protocol shared with the Android app (net/Protocol.kt). Little-endian.</summary>
public static class Protocol
{
    public const int StatePort = 39500;
    public const int DiscoveryPort = 39501;

    public const byte TypeState = 1;
    public const byte TypeRumble = 2;
    public const byte TypeDiscover = 3;
    public const byte TypeDiscoverReply = 4;
    public const byte Version = 1;
    public const int StateSize = 20;

    public static bool IsPacket(ReadOnlySpan<byte> d, byte type) =>
        d.Length >= 3 && d[0] == (byte)'H' && d[1] == (byte)'X' && d[2] == type;

    public static bool TryParseState(ReadOnlySpan<byte> d, out PadState s)
    {
        s = default;
        if (d.Length < StateSize || !IsPacket(d, TypeState)) return false;
        s = new PadState(
            Seq: BinaryPrimitives.ReadUInt32LittleEndian(d[4..]),
            Buttons: BinaryPrimitives.ReadUInt16LittleEndian(d[8..]),
            LeftTrigger: d[10],
            RightTrigger: d[11],
            LX: BinaryPrimitives.ReadInt16LittleEndian(d[12..]),
            LY: BinaryPrimitives.ReadInt16LittleEndian(d[14..]),
            RX: BinaryPrimitives.ReadInt16LittleEndian(d[16..]),
            RY: BinaryPrimitives.ReadInt16LittleEndian(d[18..]));
        return true;
    }

    public static byte[] Rumble(byte large, byte small) =>
        new[] { (byte)'H', (byte)'X', TypeRumble, large, small };

    public static byte[] DiscoverReply(string machineName)
    {
        var name = System.Text.Encoding.UTF8.GetBytes(machineName);
        var buf = new byte[4 + name.Length];
        buf[0] = (byte)'H'; buf[1] = (byte)'X'; buf[2] = TypeDiscoverReply; buf[3] = Version;
        name.CopyTo(buf, 4);
        return buf;
    }
}

public readonly record struct PadState(
    uint Seq, ushort Buttons, byte LeftTrigger, byte RightTrigger,
    short LX, short LY, short RX, short RY);

/// <summary>XInput button bits as sent by the phone.</summary>
public static class Btn
{
    public const ushort Up = 0x0001, Down = 0x0002, Left = 0x0004, Right = 0x0008,
        Start = 0x0010, Back = 0x0020, LThumb = 0x0040, RThumb = 0x0080,
        LShoulder = 0x0100, RShoulder = 0x0200, Guide = 0x0400,
        A = 0x1000, B = 0x2000, X = 0x4000, Y = 0x8000;
}
