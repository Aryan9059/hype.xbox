using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;

namespace HypeXboxServer;

/// <summary>Owns the virtual Xbox 360 pads, one per slot, and applies phone state to them.</summary>
public sealed class ControllerBridge : IDisposable
{
    /// <summary>XInput only exposes four controllers, so more would be invisible to most games.</summary>
    public const int MaxPads = 4;

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
    private readonly IXbox360Controller?[] _pads = new IXbox360Controller?[MaxPads];
    private readonly object _gate = new();

    /// <summary>Raised from a driver thread with (slot, largeMotor, smallMotor). Handlers must not wait on a lock
    /// held around <see cref="Unplug"/>, since removing a pad may wait for its feedback thread.</summary>
    public event Action<int, byte, byte>? Rumble;

    public ControllerBridge()
    {
        _client = new ViGEmClient(); // throws VigemBusNotFoundException when the driver is missing
        Plug(0); // the first pad is always present so games see it before a phone joins
    }

    /// <summary>Plugs in the slot's pad if it is not already there.</summary>
    public void Plug(int slot)
    {
        lock (_gate)
        {
            if (_pads[slot] != null) return;
            var pad = _client.CreateXbox360Controller();
            pad.AutoSubmitReport = false;
            pad.FeedbackReceived += (_, e) => Rumble?.Invoke(slot, e.LargeMotor, e.SmallMotor);
            pad.Connect();
            _pads[slot] = pad;
            Apply(slot, default);
        }
    }

    public void Unplug(int slot)
    {
        lock (_gate)
        {
            var pad = _pads[slot];
            if (pad == null) return;
            _pads[slot] = null;
            Remove(pad);
        }
    }

    public void Apply(int slot, PadState s)
    {
        lock (_gate)
        {
            var pad = _pads[slot];
            if (pad == null) return;
            foreach (var (mask, button) in Map)
                pad.SetButtonState(button, (s.Buttons & mask) != 0);
            pad.SetSliderValue(Xbox360Slider.LeftTrigger, s.LeftTrigger);
            pad.SetSliderValue(Xbox360Slider.RightTrigger, s.RightTrigger);
            pad.SetAxisValue(Xbox360Axis.LeftThumbX, s.LX);
            pad.SetAxisValue(Xbox360Axis.LeftThumbY, s.LY);
            pad.SetAxisValue(Xbox360Axis.RightThumbX, s.RX);
            pad.SetAxisValue(Xbox360Axis.RightThumbY, s.RY);
            pad.SubmitReport();
        }
    }

    private static void Remove(IXbox360Controller pad)
    {
        try { pad.Disconnect(); } catch { }
        (pad as IDisposable)?.Dispose();
    }

    public void Dispose()
    {
        lock (_gate)
        {
            for (var i = 0; i < _pads.Length; i++)
            {
                if (_pads[i] is { } pad) Remove(pad);
                _pads[i] = null;
            }
            _client.Dispose();
        }
    }
}
