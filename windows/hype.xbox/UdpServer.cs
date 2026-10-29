using System.Net;
using System.Net.Sockets;

namespace HypeXboxServer;

/// <summary>
/// Receives phone state on <see cref="Protocol.StatePort"/>, answers discovery broadcasts on
/// <see cref="Protocol.DiscoveryPort"/>, and resets the pad when the phone goes quiet.
/// </summary>
public sealed class UdpServer : IDisposable
{
    private static readonly TimeSpan Timeout = TimeSpan.FromSeconds(1);

    private readonly ControllerBridge _bridge;
    private readonly UdpClient _state = new(new IPEndPoint(IPAddress.Any, Protocol.StatePort));
    private readonly UdpClient _discovery;
    private readonly CancellationTokenSource _cts = new();
    private long _lastPacketTicks;
    private int _packetCount;
    private uint _lastSeq;
    private bool _haveSeq;
    private volatile IPEndPoint? _phone;

    public event Action<PadState>? StateReceived;
    public event Action<bool>? ConnectionChanged;
    public IPEndPoint? Phone => _phone;

    public UdpServer(ControllerBridge bridge)
    {
        _bridge = bridge;
        _discovery = new UdpClient();
        _discovery.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
        _discovery.Client.Bind(new IPEndPoint(IPAddress.Any, Protocol.DiscoveryPort));
        _bridge.Rumble += SendRumble;
    }

    public void Start()
    {
        _ = Task.Run(StateLoop);
        _ = Task.Run(DiscoveryLoop);
        _ = Task.Run(WatchdogLoop);
    }

    /// <summary>Packets received since the last call; used for the packets/sec readout.</summary>
    public int TakePacketCount() => Interlocked.Exchange(ref _packetCount, 0);

    private async Task StateLoop()
    {
        while (!_cts.IsCancellationRequested)
        {
            UdpReceiveResult r;
            try { r = await _state.ReceiveAsync(_cts.Token); }
            catch (OperationCanceledException) { return; }
            catch (ObjectDisposedException) { return; }
            catch (SocketException) { continue; }

            if (!Protocol.TryParseState(r.Buffer, out var s)) continue;
            // Drop reordered packets (signed difference handles seq wrap-around).
            if (_haveSeq && (int)(s.Seq - _lastSeq) <= 0) continue;
            _haveSeq = true;
            _lastSeq = s.Seq;

            var wasConnected = _phone != null;
            _phone = r.RemoteEndPoint;
            Interlocked.Exchange(ref _lastPacketTicks, Environment.TickCount64);
            Interlocked.Increment(ref _packetCount);
            _bridge.Apply(s);
            StateReceived?.Invoke(s);
            if (!wasConnected) ConnectionChanged?.Invoke(true);
        }
    }

    private async Task DiscoveryLoop()
    {
        var reply = Protocol.DiscoverReply(Environment.MachineName);
        while (!_cts.IsCancellationRequested)
        {
            UdpReceiveResult r;
            try { r = await _discovery.ReceiveAsync(_cts.Token); }
            catch (OperationCanceledException) { return; }
            catch (ObjectDisposedException) { return; }
            catch (SocketException) { continue; }

            if (Protocol.IsPacket(r.Buffer, Protocol.TypeDiscover))
                try { await _discovery.SendAsync(reply, r.RemoteEndPoint, _cts.Token); } catch { }
        }
    }

    private async Task WatchdogLoop()
    {
        using var timer = new PeriodicTimer(TimeSpan.FromMilliseconds(250));
        try
        {
            while (await timer.WaitForNextTickAsync(_cts.Token))
            {
                if (_phone == null) continue;
                var idle = Environment.TickCount64 - Interlocked.Read(ref _lastPacketTicks);
                if (idle < Timeout.TotalMilliseconds) continue;
                // Phone went quiet: release everything so nothing stays stuck.
                _phone = null;
                _haveSeq = false;
                _bridge.Apply(default);
                StateReceived?.Invoke(default);
                ConnectionChanged?.Invoke(false);
            }
        }
        catch (OperationCanceledException) { }
    }

    private void SendRumble(byte large, byte small)
    {
        var phone = _phone;
        if (phone == null) return;
        var packet = Protocol.Rumble(large, small);
        try { _state.Send(packet, packet.Length, phone); } catch { }
    }

    public void Dispose()
    {
        _bridge.Rumble -= SendRumble;
        _cts.Cancel();
        _state.Dispose();
        _discovery.Dispose();
        _cts.Dispose();
    }
}
