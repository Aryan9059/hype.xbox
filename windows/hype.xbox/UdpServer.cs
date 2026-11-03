using System.Net;
using System.Net.Sockets;

namespace HypeXboxServer;

/// <summary>
/// Receives phone state on <see cref="Protocol.StatePort"/>, answers discovery broadcasts on
/// <see cref="Protocol.DiscoveryPort"/>, and gives each phone (identified by its source endpoint)
/// its own virtual pad. A pad is reset when its phone goes quiet.
/// </summary>
public sealed class UdpServer : IDisposable
{
    private const int TimeoutMs = 1000;
    /// <summary>Phones send at least every 50 ms, so this much silence plus a new port on the same address
    /// means the app reconnected on a new socket rather than a second app sharing the address.</summary>
    private const int HandoverMs = 250;
    /// <summary>How long an extra pad stays plugged in without a phone, so a brief drop doesn't look like an
    /// unplug to the game. The first pad is never removed.</summary>
    private const int UnplugAfterMs = 30_000;

    private sealed class Slot
    {
        public volatile IPEndPoint? Phone; // read without the lock by the rumble path
        public IPAddress? LastAddress;
        public PadState State;
        public uint LastSeq;
        public bool HaveSeq;
        public long LastPacketTicks;
        public bool Plugged;
    }

    private readonly ControllerBridge _bridge;
    private readonly UdpClient _state = new(new IPEndPoint(IPAddress.Any, Protocol.StatePort));
    private readonly UdpClient _discovery;
    private readonly CancellationTokenSource _cts = new();
    private readonly Slot[] _slots = new Slot[ControllerBridge.MaxPads];
    private readonly object _gate = new();
    private int _packetCount;

    public UdpServer(ControllerBridge bridge)
    {
        _bridge = bridge;
        for (var i = 0; i < _slots.Length; i++) _slots[i] = new Slot();
        _slots[0].Plugged = true; // ControllerBridge keeps the first pad plugged in
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

    /// <summary>The phone on a slot and its latest input, or false when the slot is free.</summary>
    public bool TryGetPhone(int slot, out IPEndPoint phone, out PadState state)
    {
        lock (_gate)
        {
            var s = _slots[slot];
            phone = s.Phone!;
            state = s.State;
            return phone != null;
        }
    }

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

            lock (_gate)
            {
                var index = FindSlot(r.RemoteEndPoint);
                if (index < 0) continue; // every pad is taken

                var slot = _slots[index];
                if (!r.RemoteEndPoint.Equals(slot.Phone) && !Claim(index, r.RemoteEndPoint)) continue;
                // Drop reordered packets (signed difference handles seq wrap-around).
                if (slot.HaveSeq && (int)(s.Seq - slot.LastSeq) <= 0) continue;
                slot.HaveSeq = true;
                slot.LastSeq = s.Seq;
                slot.LastPacketTicks = Environment.TickCount64;
                slot.State = s;
                Interlocked.Increment(ref _packetCount);
                try { _bridge.Apply(index, s); } catch { }
            }
        }
    }

    /// <summary>The slot a packet belongs to, or -1 when all are taken. Caller holds the lock.</summary>
    private int FindSlot(IPEndPoint from)
    {
        var now = Environment.TickCount64;
        var index = Array.FindIndex(_slots, s => from.Equals(s.Phone));
        // Same device on a new socket: keep its pad instead of moving it to another player.
        if (index < 0)
            index = Array.FindIndex(_slots, s => s.Phone != null && from.Address.Equals(s.Phone.Address) && now - s.LastPacketTicks >= HandoverMs);
        // Back after a recent drop: return to its old pad, so two phones dropping together don't swap players.
        if (index < 0)
            index = Array.FindIndex(_slots, s => s.Phone == null && from.Address.Equals(s.LastAddress));
        return index >= 0 ? index : Array.FindIndex(_slots, s => s.Phone == null);
    }

    /// <summary>Gives a slot to a phone, plugging in its pad if needed. Caller holds the lock.</summary>
    private bool Claim(int index, IPEndPoint phone)
    {
        var slot = _slots[index];
        if (!slot.Plugged)
        {
            try { _bridge.Plug(index); }
            catch { return false; } // leave the slot free; the phone's next packet retries
            slot.Plugged = true;
        }
        slot.Phone = phone;
        slot.LastAddress = phone.Address;
        slot.HaveSeq = false; // a new socket starts its own sequence
        return true;
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
                lock (_gate)
                {
                    var now = Environment.TickCount64;
                    for (var i = 0; i < _slots.Length; i++)
                    {
                        var slot = _slots[i];
                        var idle = now - slot.LastPacketTicks;
                        if (slot.Phone != null && idle >= TimeoutMs)
                        {
                            // Phone went quiet: release everything so nothing stays stuck.
                            slot.Phone = null;
                            slot.HaveSeq = false;
                            slot.State = default;
                            try { _bridge.Apply(i, default); } catch { }
                        }
                        else if (slot.Phone == null && idle >= UnplugAfterMs)
                        {
                            // Long gone: the next phone takes the lowest free pad, whoever it is.
                            slot.LastAddress = null;
                            if (i == 0 || !slot.Plugged) continue;
                            slot.Plugged = false;
                            try { _bridge.Unplug(i); } catch { }
                        }
                    }
                }
            }
        }
        catch (OperationCanceledException) { }
    }

    private void SendRumble(int slot, byte large, byte small)
    {
        var phone = _slots[slot].Phone;
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
