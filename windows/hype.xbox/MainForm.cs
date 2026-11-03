using System.Diagnostics;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Numerics;
using Microsoft.Win32;

namespace HypeXboxServer;

public sealed class MainForm : Form
{
    private const string AppName = "hype.xbox";
    private const string ViGEmUrl = "https://github.com/nefarius/ViGEmBus/releases";

    private enum Status { Waiting, Connected, Error }

    /// <summary>One controller's caption and live view in the input card.</summary>
    private sealed record Tile(FlowLayoutPanel Panel, Label Caption, InputView View);

    private readonly BrandHeader _header = new();
    private readonly Dot _dot = new() { Margin = new Padding(0, 0, 10, 0), Anchor = AnchorStyles.Left };
    private readonly Label _statusTitle = new() { AutoSize = true, Font = Theme.Display(15, FontStyle.Bold), BackColor = Color.Transparent };
    private readonly Label _statusSub = new() { AutoSize = true, Font = Theme.Body(), BackColor = Color.Transparent };
    private readonly FluentButton _action = new() { Primary = true, Visible = false, Anchor = AnchorStyles.Right };
    private readonly TableLayoutPanel _ipRows = new() { AutoSize = true, ColumnCount = 1, Dock = DockStyle.Fill, BackColor = Color.Transparent };
    private readonly Label _hint;
    private readonly TableLayoutPanel _tileGrid = new() { AutoSize = true, ColumnCount = 2, RowCount = 2, BackColor = Color.Transparent, Margin = new Padding(0) };
    private readonly Tile[] _tiles = new Tile[ControllerBridge.MaxPads];
    private int _shownSlots; // bitmask of the slots whose tile is showing
    private readonly Clip _reveal = new();
    private readonly List<Action> _themers = new();
    private readonly List<CardPanel> _cards = new();
    private CardPanel _statusCard = null!;
    private CardPanel _addressCard = null!;
    private CardPanel _inputCard = null!;

    private readonly NotifyIcon _tray = new() { Text = AppName };
    private readonly System.Windows.Forms.Timer _ui = new() { Interval = 500 };
    private readonly System.Windows.Forms.Timer _anim = new() { Interval = 15 };
    private readonly Stopwatch _clock = Stopwatch.StartNew();

    private ControllerBridge? _bridge;
    private UdpServer? _server;
    private Status _status = Status.Waiting;
    private Action? _actionClick;

    // animation state
    private double _lastTick;
    private float _enter, _open, _phase, _tintAmount, _dotLevel;
    private Color _tint = Theme.Warn;

    public MainForm()
    {
        Text = AppName;
        AutoScaleMode = AutoScaleMode.Dpi;
        ClientSize = new Size(500, 360);
        StartPosition = FormStartPosition.CenterScreen;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        DoubleBuffered = true;
        Font = Theme.Body();
        Icon = TryAppIcon();

        _hint = Caption($"Your phone finds this PC automatically. If it does not, enter one of these addresses in the app. Up to {ControllerBridge.MaxPads} phones can connect, each as its own controller.");
        _hint.Margin = new Padding(0, 0, 0, 10);

        _statusCard = BuildStatusCard();
        _addressCard = BuildAddressCard();
        _inputCard = BuildInputCard();
        _reveal.Controls.Add(_inputCard);
        Controls.AddRange(new Control[] { _header, _statusCard, _addressCard, _reveal });

        BuildTray();
        _action.Click += (_, _) => _actionClick?.Invoke();

        ApplyTheme();
        SystemEvents.UserPreferenceChanged += OnPreferenceChanged;

        Load += (_, _) => { StartServer(); LayoutRoot(); CenterToScreen(); _lastTick = _clock.Elapsed.TotalSeconds; _anim.Start(); };
        DpiChanged += (_, _) => LayoutRoot();
        HandleCreated += (_, _) => Dwm.Apply(Handle, Theme.Dark);
        Resize += (_, _) => { if (WindowState == FormWindowState.Minimized) Hide(); };
        FormClosing += (_, _) => Shutdown();
        _ui.Tick += (_, _) => RefreshUi();
        _anim.Tick += (_, _) => Animate();
        _ui.Start();
    }

    // ---- layout ------------------------------------------------------------------------------

    private int S(int v) => (int)Math.Round(v * DeviceDpi / 96f);

    private CardPanel NewCard()
    {
        var card = new CardPanel { Padding = new Padding(18, 16, 18, 16) };
        _cards.Add(card);
        return card;
    }

    private Label Heading(string text)
    {
        var l = new Label { Text = text, AutoSize = true, Font = Theme.Body(11f, FontStyle.Bold), BackColor = Color.Transparent, Margin = new Padding(0, 0, 0, 4) };
        l.ForeColor = Theme.Text;
        _themers.Add(() => l.ForeColor = Theme.Text);
        return l;
    }

    private Label Caption(string text, float pt = 9f)
    {
        var l = new Label { Text = text, AutoSize = true, Font = Theme.Body(pt), BackColor = Color.Transparent };
        l.ForeColor = Theme.SubText;
        _themers.Add(() => l.ForeColor = Theme.SubText);
        return l;
    }

    /// <summary>Stacks header and cards by hand so the live-input card can slide in and the window can follow.</summary>
    private void LayoutRoot()
    {
        int pad = S(16), gap = S(12), cw = S(500) - 2 * pad, inner = cw - S(36);
        _hint.MaximumSize = new Size(inner, 0);
        // One controller gets the full width; several share a two-column grid so the window stays on screen.
        var tileGap = S(12);
        var tileW = BitOperations.PopCount((uint)_shownSlots) > 1 ? (inner - tileGap) / 2 : inner;
        var shown = 0;
        for (var i = 0; i < _tiles.Length; i++)
        {
            var tile = _tiles[i];
            tile.View.Size = new Size(tileW, (int)(tileW * 168f / 432f));
            tile.Caption.MaximumSize = new Size(tileW, 0);
            if ((_shownSlots & (1 << i)) == 0) continue;
            tile.Panel.Margin = new Padding(0, S(8), shown % 2 == 0 && tileW < inner ? tileGap : 0, 0);
            shown++;
        }
        _statusSub.MaximumSize = new Size(inner - S(54) - (_action.Visible ? _action.Width + S(8) : 0), 0);

        var contentH = _inputCard.FitHeight(cw);
        var ease = Theme.Ease(_open);
        var revealH = (int)Math.Round(contentH * ease);
        var heights = new[] { S(62), _statusCard.FitHeight(cw), _addressCard.FitHeight(cw), revealH };
        var items = new Control[] { _header, _statusCard, _addressCard, _reveal };

        SuspendLayout();
        var y = pad;
        for (var i = 0; i < items.Length; i++)
        {
            var offset = (int)((1f - Theme.Ease((_enter - i * 0.09f) / 0.4f)) * S(22));
            items[i].SetBounds(pad, y + offset, cw, heights[i]);
            y += heights[i] + (i < 2 ? gap : i == 2 ? (int)Math.Round(gap * ease) : 0);
        }
        _inputCard.SetBounds(0, 0, cw, contentH);
        _reveal.Visible = revealH > 0;
        y += pad;

        var size = new Size(S(500), y);
        if (ClientSize != size) ClientSize = size;
        ResumeLayout(true);
    }

    private CardPanel BuildStatusCard()
    {
        var card = NewCard();
        var grid = new TableLayoutPanel { AutoSize = true, ColumnCount = 3, RowCount = 1, Dock = DockStyle.Fill, BackColor = Color.Transparent };
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        grid.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));

        var text = new FlowLayoutPanel { AutoSize = true, FlowDirection = FlowDirection.TopDown, WrapContents = false, BackColor = Color.Transparent, Anchor = AnchorStyles.Left };
        text.Controls.Add(_statusTitle);
        text.Controls.Add(_statusSub);

        grid.Controls.Add(_dot, 0, 0);
        grid.Controls.Add(text, 1, 0);
        grid.Controls.Add(_action, 2, 0);
        card.Controls.Add(grid);

        _themers.Add(() => { _statusTitle.ForeColor = Theme.Text; _statusSub.ForeColor = Theme.SubText; });
        return card;
    }

    private CardPanel BuildAddressCard()
    {
        var card = NewCard();
        var stack = new TableLayoutPanel { AutoSize = true, ColumnCount = 1, Dock = DockStyle.Fill, BackColor = Color.Transparent };
        stack.Controls.Add(Heading("Connect your phone"));
        stack.Controls.Add(_hint);
        stack.Controls.Add(_ipRows);
        card.Controls.Add(stack);
        return card;
    }

    private CardPanel BuildInputCard()
    {
        var card = NewCard();
        var stack = new TableLayoutPanel { AutoSize = true, ColumnCount = 1, Dock = DockStyle.Fill, BackColor = Color.Transparent };
        stack.Controls.Add(Heading("Live input"));
        for (var i = 0; i < _tiles.Length; i++)
        {
            var panel = new FlowLayoutPanel { AutoSize = true, FlowDirection = FlowDirection.TopDown, WrapContents = false, BackColor = Color.Transparent, Visible = false };
            var caption = Caption("", 8.5f);
            caption.Margin = new Padding(0);
            var view = new InputView { Margin = new Padding(0, 6, 0, 0) };
            panel.Controls.Add(caption);
            panel.Controls.Add(view);
            _tileGrid.Controls.Add(panel);
            _tiles[i] = new Tile(panel, caption, view);
        }
        stack.Controls.Add(_tileGrid);
        card.Controls.Add(stack);
        return card;
    }

    /// <summary>Shows a tile per connected phone, filling the grid in slot order.</summary>
    private void ShowTiles(List<(int Slot, IPEndPoint Phone)> phones)
    {
        var slots = 0;
        foreach (var (slot, phone) in phones)
        {
            slots |= 1 << slot;
            _tiles[slot].Caption.Text = $"Controller {slot + 1}  ·  {phone.Address}";
        }
        if (slots == _shownSlots) return;
        _shownSlots = slots;

        _tileGrid.SuspendLayout();
        for (var i = 0; i < _tiles.Length; i++)
            _tiles[i].Panel.Visible = (slots & (1 << i)) != 0;
        for (var i = 0; i < phones.Count; i++)
            _tileGrid.SetCellPosition(_tiles[phones[i].Slot].Panel, new TableLayoutPanelCellPosition(i % 2, i / 2));
        _tileGrid.ResumeLayout();
        LayoutRoot();
    }

    private void AddAddressRow(string name, string ip)
    {
        var inset = new InsetPanel { Dock = DockStyle.Fill, Padding = new Padding(14, 8, 8, 8), Margin = new Padding(0, 3, 0, 3) };
        var row = new TableLayoutPanel { AutoSize = true, ColumnCount = 2, Dock = DockStyle.Fill, BackColor = Color.Transparent };
        row.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        row.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));

        var text = new FlowLayoutPanel { AutoSize = true, FlowDirection = FlowDirection.TopDown, WrapContents = false, BackColor = Color.Transparent, Anchor = AnchorStyles.Left };
        var label = new Label { Text = ip, AutoSize = true, Font = new Font("Cascadia Mono", 11.5f), BackColor = Color.Transparent, Margin = new Padding(0) };
        label.ForeColor = Theme.Text;
        _themers.Add(() => label.ForeColor = Theme.Text);
        text.Controls.Add(label);
        if (name.Length > 0)
        {
            var caption = Caption(name, 8.5f);
            caption.Margin = new Padding(0);
            text.Controls.Add(caption);
        }
        row.Controls.Add(text, 0, 0);

        if (ip.Contains('.'))
        {
            var copy = new FluentButton { Text = "Copy", Anchor = AnchorStyles.Right };
            copy.Click += async (_, _) =>
            {
                try { Clipboard.SetText(ip); } catch { return; }
                copy.Text = "Copied";
                await Task.Delay(1500);
                if (!copy.IsDisposed) copy.Text = "Copy";
            };
            row.Controls.Add(copy, 1, 0);
        }
        inset.Controls.Add(row);
        _ipRows.Controls.Add(inset);
    }

    private void BuildTray()
    {
        _tray.Icon = Icon ?? SystemIcons.Application;
        var menu = new ContextMenuStrip();
        menu.Items.Add($"Open {AppName}", null, (_, _) => RestoreFromTray());
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("Exit", null, (_, _) => Close());
        _tray.ContextMenuStrip = menu;
        _tray.Visible = true;
        _tray.DoubleClick += (_, _) => RestoreFromTray();
    }

    private void RestoreFromTray()
    {
        Show();
        WindowState = FormWindowState.Normal;
        Activate();
    }

    private static Icon? TryAppIcon()
    {
        try { return Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { return null; }
    }

    // ---- theming -----------------------------------------------------------------------------

    private void OnPreferenceChanged(object? sender, UserPreferenceChangedEventArgs e)
    {
        if (e.Category is not (UserPreferenceCategory.General or UserPreferenceCategory.Color)) return;
        if (IsHandleCreated) BeginInvoke(() => { Theme.Refresh(); ApplyTheme(); Dwm.Apply(Handle, Theme.Dark); });
    }

    private void ApplyTheme()
    {
        BackColor = Theme.Bg;
        _reveal.BackColor = Theme.Bg;
        foreach (var t in _themers) t();
        foreach (var c in _cards) c.Invalidate(true);
        _header.Invalidate();
        Invalidate(true);
    }

    private void SetStatus(Status status, string title, string sub)
    {
        _status = status;
        if (_statusTitle.Text == title && _statusSub.Text == sub) return;
        _statusTitle.Text = title;
        _statusSub.Text = sub;
        if (IsHandleCreated) LayoutRoot();
    }

    private void ShowAction(string text, Action onClick)
    {
        _action.Text = text;
        _actionClick = onClick;
        _action.Visible = true;
    }

    // ---- animation ---------------------------------------------------------------------------

    private void Animate()
    {
        var now = _clock.Elapsed.TotalSeconds;
        var dt = (float)Math.Min(now - _lastTick, 0.1);
        _lastTick = now;
        if (!Visible || WindowState == FormWindowState.Minimized) return;

        var layout = false;
        if (_enter < 1.5f)
        {
            _enter += dt;
            layout = true;
        }

        var (target, amount, level) = _status switch
        {
            Status.Connected => (Theme.Ok, 0.55f, 0.55f),
            Status.Error => (Theme.Danger, 1f, 0f),
            _ => (Theme.Warn, 1f, 1f),
        };
        var k = 1f - MathF.Exp(-dt * 6f);
        _tint = Theme.Mix(_tint, target, k);
        _tintAmount += (amount - _tintAmount) * k;
        _dotLevel += (level - _dotLevel) * k;
        _phase = (_phase + dt / (_status == Status.Waiting ? 1.5f : 2.6f)) % 1f;
        var pulse = _status == Status.Waiting ? 0.5f + 0.5f * MathF.Sin((float)now * MathF.Tau / 2.2f) : 0.35f;
        _statusCard.SetLook(_tint, _tintAmount, pulse);
        _dot.Set(_tint, 0f, 0f);

        var goal = _status == Status.Connected ? 1f : 0f;
        var step = dt / 0.45f;
        var open = goal > _open ? Math.Min(goal, _open + step) : Math.Max(goal, _open - step);
        if (open != _open) { _open = open; layout = true; }
        if (layout) LayoutRoot();

        if (_open > 0f && _server != null)
        {
            for (var i = 0; i < _tiles.Length; i++)
            {
                if ((_shownSlots & (1 << i)) == 0) continue;
                _server.TryGetPhone(i, out _, out var pad); // a free slot reads as released, so the view settles
                _tiles[i].View.Step(dt, pad);
            }
        }
    }

    // ---- server ------------------------------------------------------------------------------

    private void StartServer()
    {
        foreach (var (name, ip) in LocalAddresses()) AddAddressRow(name, ip);
        SetStatus(Status.Waiting, "Waiting for phone", $"Open the {AppName} app on the same Wi-Fi network.");
        try
        {
            _bridge = new ControllerBridge();
            _server = new UdpServer(_bridge);
            _server.Start();
        }
        catch (Nefarius.ViGEm.Client.Exceptions.VigemBusNotFoundException)
        {
            SetStatus(Status.Error, "ViGEmBus driver not installed", "Needed to create the virtual Xbox controller.");
            ShowAction("Download driver", () => Process.Start(new ProcessStartInfo(ViGEmUrl) { UseShellExecute = true }));
            if (MessageBox.Show(
                    $"{AppName} needs the ViGEmBus driver to create a virtual Xbox controller.\n\nOpen the download page now? Install it, reboot if asked, then start this app again.",
                    AppName, MessageBoxButtons.YesNo, MessageBoxIcon.Warning) == DialogResult.Yes)
                Process.Start(new ProcessStartInfo(ViGEmUrl) { UseShellExecute = true });
        }
        catch (SocketException e)
        {
            SetStatus(Status.Error, "Ports 39500/39501 are in use", e.Message);
        }
    }

    private void RefreshUi()
    {
        if (_server == null) return;
        var phones = new List<(int Slot, IPEndPoint Phone)>();
        for (var i = 0; i < _tiles.Length; i++)
            if (_server.TryGetPhone(i, out var phone, out _)) phones.Add((i, phone));
        var pps = _server.TakePacketCount() * 2; // timer is 500 ms

        if (phones.Count == 0)
        {
            // The tiles stay as they are while the card slides shut.
            SetStatus(Status.Waiting, "Waiting for phone", $"Open the {AppName} app on the same Wi-Fi network.");
            return;
        }
        ShowTiles(phones);
        var who = phones.Count == 1 ? phones[0].Phone.Address.ToString() : $"{phones.Count} phones";
        SetStatus(Status.Connected, "Connected", $"{who}  ·  {pps} packets/sec");
    }

    private static IEnumerable<(string Name, string Ip)> LocalAddresses() =>
        NetworkInterface.GetAllNetworkInterfaces()
            .Where(n => n.OperationalStatus == OperationalStatus.Up &&
                        n.NetworkInterfaceType is NetworkInterfaceType.Wireless80211 or NetworkInterfaceType.Ethernet)
            .SelectMany(n => n.GetIPProperties().UnicastAddresses.Select(a => (n.Name, a.Address)))
            .Where(a => a.Address.AddressFamily == AddressFamily.InterNetwork)
            .Select(a => (a.Name, a.Address.ToString()))
            .DefaultIfEmpty(("", "(no network found)"));

    private void Shutdown()
    {
        SystemEvents.UserPreferenceChanged -= OnPreferenceChanged;
        _ui.Stop();
        _anim.Stop();
        _tray.Visible = false;
        _tray.Dispose();
        _server?.Dispose();
        _bridge?.Dispose();
    }
}
