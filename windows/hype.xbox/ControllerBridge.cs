using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;

namespace HypeXboxServer;

/// <summary>Owns the virtual Xbox 360 pad and applies phone state to it.</summary>
public sealed class ControllerBridge : IDisposable
{
    private static readonly (ushort Mask, Xbox360Button Button)[] Map =
    {
        (Btn.Up, Xbox360Button.Up), (Btn.Down, Xbox360Button.Down),
        (Btn.Left, Xbox360Button.Left), (Btn.Right, Xbox360Button.Right),
        (Btn.Start, Xbox360Button.Start), (Btn.Back, Xbox360Button.Back),
        (Btn.LThumb, Xbox360Button.LeftThumb), (Btn.RThumb, Xbox360Button.RightThumb),
        (Btn.LShoulder, Xbox360Button.LeftShoulder), (Btn.RShoulder, Xbox360Button.RightShoulder),
        (Btn.Guide, Xbox360Button.Guide),
        (Btn.A, Xbox360Button.A), (Btn.B, Xbox360Button.B),
        (Btn.X, Xbox360Button.X), (Btn.Y, Xbox360Button.Y),
    };

    private readonly ViGEmClient _client;
    private readonly IXbox360Controller _pad;
    private readonly object _gate = new();

    /// <summary>Raised from a driver thread with (largeMotor, smallMotor).</summary>
    public event Action<byte, byte>? Rumble;

    public ControllerBridge()
    {
        _client = new ViGEmClient(); // throws VigemBusNotFoundException when the driver is missing
        _pad = _client.CreateXbox360Controller();
        _pad.AutoSubmitReport = false;
        _pad.FeedbackReceived += (_, e) => Rumble?.Invoke(e.LargeMotor, e.SmallMotor);
        _pad.Connect();
        Apply(default);
    }

    public void Apply(PadState s)
    {
        lock (_gate)
        {
            foreach (var (mask, button) in Map)
                _pad.SetButtonState(button, (s.Buttons & mask) != 0);
            _pad.SetSliderValue(Xbox360Slider.LeftTrigger, s.LeftTrigger);
            _pad.SetSliderValue(Xbox360Slider.RightTrigger, s.RightTrigger);
            _pad.SetAxisValue(Xbox360Axis.LeftThumbX, s.LX);
            _pad.SetAxisValue(Xbox360Axis.LeftThumbY, s.LY);
            _pad.SetAxisValue(Xbox360Axis.RightThumbX, s.RX);
            _pad.SetAxisValue(Xbox360Axis.RightThumbY, s.RY);
            _pad.SubmitReport();
        }
    }

    public void Dispose()
    {
        try { _pad.Disconnect(); } catch { }
        _client.Dispose();
    }
}
