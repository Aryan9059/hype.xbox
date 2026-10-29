using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.Runtime.InteropServices;
using Microsoft.Win32;

namespace HypeXboxServer;

/// <summary>Windows 11 look: follows the system light/dark setting.</summary>
internal static class Theme
{
    public static bool Dark { get; private set; }
    public static Color Bg, Card, CardBorder, Inset, Text, SubText, Button, ButtonHover, ButtonPressed, ButtonBorder;
    public static Color Accent, AccentHover, AccentPressed, OnAccent, Brand, OnBrand, Ok, Warn, Danger;
    public static Color FaceA, FaceB, FaceX, FaceY;

    public static string BodyName { get; } = Pick("Segoe UI Variable Text", "Segoe UI");
    private static readonly string DisplayFamily = Pick("Segoe UI Variable Display", "Segoe UI");

    static Theme() => Refresh();

    public static Font Body(float pt = 9.5f, FontStyle style = FontStyle.Regular) => new(BodyName, pt, style);
    public static Font Display(float pt, FontStyle style = FontStyle.Regular) => new(DisplayFamily, pt, style);

    public static void Refresh()
    {
        Dark = !IsLight();
        if (Dark)
        {
            Bg = Rgb(0x1B, 0x1B, 0x1B);
            Card = Rgb(0x2B, 0x2B, 0x2B);
            CardBorder = Rgb(0x3A, 0x3A, 0x3A);
            Inset = Rgb(0x33, 0x33, 0x33);
            Text = Rgb(0xFF, 0xFF, 0xFF);
            SubText = Rgb(0xC5, 0xC5, 0xC5);
            Button = Rgb(0x38, 0x38, 0x38);
            ButtonHover = Rgb(0x44, 0x44, 0x44);
            ButtonPressed = Rgb(0x33, 0x33, 0x33);
            ButtonBorder = Rgb(0x4A, 0x4A, 0x4A);
            Accent = Rgb(0x10, 0x7C, 0x10);
            AccentHover = Rgb(0x13, 0x8F, 0x13);
            AccentPressed = Rgb(0x0E, 0x6A, 0x0E);
            OnAccent = Color.White;
            Brand = Rgb(0x78, 0xBB, 0x45);
            OnBrand = Rgb(0x10, 0x10, 0x10);
            Ok = Rgb(0x6C, 0xCB, 0x5F);
            Warn = Rgb(0xFC, 0xC4, 0x00);
            Danger = Rgb(0xFF, 0x99, 0xA4);
            FaceA = Rgb(0x6C, 0xCB, 0x5F); FaceB = Rgb(0xFF, 0x6B, 0x6B);
            FaceX = Rgb(0x5C, 0xA8, 0xFF); FaceY = Rgb(0xFF, 0xD2, 0x3F);
        }
        else
        {
            Bg = Rgb(0xF3, 0xF3, 0xF3);
            Card = Color.White;
            CardBorder = Rgb(0xE5, 0xE5, 0xE5);
            Inset = Rgb(0xF3, 0xF3, 0xF3);
            Text = Rgb(0x1A, 0x1A, 0x1A);
            SubText = Rgb(0x5F, 0x5F, 0x5F);
            Button = Rgb(0xFB, 0xFB, 0xFB);
            ButtonHover = Rgb(0xEE, 0xEE, 0xEE);
            ButtonPressed = Rgb(0xE6, 0xE6, 0xE6);
            ButtonBorder = Rgb(0xD6, 0xD6, 0xD6);
            Accent = Rgb(0x10, 0x7C, 0x10);
            AccentHover = Rgb(0x0E, 0x6F, 0x0E);
            AccentPressed = Rgb(0x0C, 0x5F, 0x0C);
            OnAccent = Color.White;
            Brand = Rgb(0x4E, 0x9A, 0x1F);
            OnBrand = Color.White;
            Ok = Rgb(0x0F, 0x7B, 0x0F);
            Warn = Rgb(0xD9, 0x8A, 0x00);
            Danger = Rgb(0xC4, 0x2B, 0x1C);
            FaceA = Rgb(0x2E, 0x9E, 0x2E); FaceB = Rgb(0xD9, 0x3B, 0x3B);
            FaceX = Rgb(0x2B, 0x7F, 0xDB); FaceY = Rgb(0xD9, 0x9A, 0x00);
        }
    }

    public static Color Mix(Color a, Color b, float t)
    {
        t = Math.Clamp(t, 0f, 1f);
        return Color.FromArgb(
            (int)(a.A + (b.A - a.A) * t), (int)(a.R + (b.R - a.R) * t),
            (int)(a.G + (b.G - a.G) * t), (int)(a.B + (b.B - a.B) * t));
    }

    public static Color Alpha(Color c, int a) => Color.FromArgb(Math.Clamp(a, 0, 255), c);

    public static float Ease(float t) => 1f - MathF.Pow(1f - Math.Clamp(t, 0f, 1f), 3f);

    private static Color Rgb(int r, int g, int b) => Color.FromArgb(r, g, b);

    private static bool IsLight()
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize");
            return key?.GetValue("AppsUseLightTheme") is not int v || v != 0;
        }
        catch { return true; }
    }

    private static string Pick(string preferred, string fallback)
    {
        foreach (var family in FontFamily.Families)
            if (family.Name == preferred) return preferred;
        return fallback;
    }
}

/// <summary>Dark title bar, Mica backdrop and rounded corners where the OS supports them (no-ops elsewhere).</summary>
internal static class Dwm
{
    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int value, int size);

    public static void Apply(IntPtr hwnd, bool dark)
    {
        Set(hwnd, 20, dark ? 1 : 0); // DWMWA_USE_IMMERSIVE_DARK_MODE
        Set(hwnd, 33, 2);            // DWMWA_WINDOW_CORNER_PREFERENCE = round
        Set(hwnd, 38, 2);            // DWMWA_SYSTEMBACKDROP_TYPE = Mica
    }

    private static void Set(IntPtr hwnd, int attr, int value) => DwmSetWindowAttribute(hwnd, attr, ref value, sizeof(int));
}

internal static class Shapes
{
    public static GraphicsPath Round(RectangleF r, float radius)
    {
        radius = Math.Max(0.5f, Math.Min(radius, Math.Min(r.Width, r.Height) / 2f));
        var d = radius * 2;
        var p = new GraphicsPath();
        p.AddArc(r.X, r.Y, d, d, 180, 90);
        p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
        p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
        p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
        p.CloseFigure();
        return p;
    }

    /// <summary>App logo from the embedded icon (null if the resource is missing).</summary>
    public static Bitmap? LoadLogo()
    {
        try
        {
            using var s = typeof(Shapes).Assembly.GetManifestResourceStream("app.ico");
            if (s == null) return null;
            using var ico = new Icon(s, 128, 128);
            return ico.ToBitmap();
        }
        catch { return null; }
    }
}

/// <summary>Panel that paints without flicker; used to clip the sliding live-input card.</summary>
internal sealed class Clip : Panel
{
    public Clip() { DoubleBuffered = true; }
}

/// <summary>Logo, wordmark and tagline at the top of the window.</summary>
internal sealed class BrandHeader : Control
{
    private static readonly Font TitleFont = Theme.Display(20, FontStyle.Bold);
    private static readonly Font TagFont = Theme.Body(9.5f);
    private const TextFormatFlags Flags = TextFormatFlags.NoPadding | TextFormatFlags.NoClipping | TextFormatFlags.SingleLine;

    public BrandHeader() { SetStyle(ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true); }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics;
        g.Clear(Theme.Bg);
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.InterpolationMode = InterpolationMode.HighQualityBicubic;
        var s = DeviceDpi / 96f;
        var x = (int)(2 * s);
        var h1 = TextRenderer.MeasureText(g, "hype.", TitleFont, Size.Empty, Flags);
        var h2 = TextRenderer.MeasureText(g, "Your phone as an Xbox controller", TagFont, Size.Empty, Flags);
        var top = (int)((Height - (h1.Height + h2.Height)) / 2f);
        TextRenderer.DrawText(g, "hype.", TitleFont, new Point(x, top), Theme.Text, Flags);
        TextRenderer.DrawText(g, "xbox", TitleFont, new Point(x + h1.Width, top), Theme.Brand, Flags);
        TextRenderer.DrawText(g, "Your phone as an Xbox controller", TagFont, new Point(x, top + h1.Height - 1), Theme.SubText, Flags);
    }
}

/// <summary>
/// Rounded surface with a hairline border, like a Windows 11 settings card. <see cref="SetLook"/> tints it
/// and adds a soft inner glow, used to highlight the connection status.
/// </summary>
internal sealed class CardPanel : Panel
{
    private Color _tint;
    private float _amount, _pulse;

    public CardPanel()
    {
        DoubleBuffered = true;
        ResizeRedraw = true;
    }

    public void SetLook(Color tint, float amount, float pulse)
    {
        if (tint == _tint && amount == _amount && pulse == _pulse) return;
        _tint = tint; _amount = amount; _pulse = pulse;
        Invalidate(true);
    }

    /// <summary>Height the card needs for its content at the given outer width.</summary>
    public int FitHeight(int width) =>
        Controls[0].GetPreferredSize(new Size(width - Padding.Horizontal, 0)).Height + Padding.Vertical;

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics;
        g.Clear(Theme.Bg);
        g.SmoothingMode = SmoothingMode.AntiAlias;
        var s = DeviceDpi / 96f;
        var rect = new RectangleF(0.5f, 0.5f, Width - 1.5f, Height - 1.5f);
        var radius = 10f * s;
        using var path = Shapes.Round(rect, radius);
        using var fill = new SolidBrush(Theme.Mix(Theme.Card, _tint, _amount * (Theme.Dark ? 0.11f : 0.08f)));
        g.FillPath(fill, path);

        using var border = new Pen(Theme.Mix(Theme.CardBorder, _tint, _amount * 0.6f));
        g.DrawPath(border, path);
    }
}

/// <summary>Subtle rounded well used for rows inside a card.</summary>
internal sealed class InsetPanel : Panel
{
    public InsetPanel()
    {
        DoubleBuffered = true;
        ResizeRedraw = true;
        AutoSize = true;
        AutoSizeMode = AutoSizeMode.GrowAndShrink;
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics;
        g.Clear(Theme.Card);
        g.SmoothingMode = SmoothingMode.AntiAlias;
        using var path = Shapes.Round(new RectangleF(0, 0, Width - 1, Height - 1), 7f * DeviceDpi / 96f);
        using var fill = new SolidBrush(Theme.Mix(Theme.Card, Theme.Text, 0.06f));
        g.FillPath(fill, path);
    }
}

/// <summary>Flat rounded button in the Windows 11 style; <see cref="Primary"/> uses the accent colour.</summary>
internal sealed class FluentButton : Button
{
    private readonly System.Windows.Forms.Timer _anim = new() { Interval = 15 };
    private bool _hover, _down;
    private float _hoverT;

    public bool Primary { get; set; }

    public FluentButton()
    {
        SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);
        FlatStyle = FlatStyle.Flat;
        Cursor = Cursors.Hand;
        AutoSize = true;
        AutoSizeMode = AutoSizeMode.GrowAndShrink;
        Padding = new Padding(12, 5, 12, 5);
        BackColor = Color.Transparent;
        _anim.Tick += (_, _) =>
        {
            var target = _hover ? 1f : 0f;
            _hoverT += Math.Sign(target - _hoverT) * 0.2f;
            if (Math.Abs(target - _hoverT) < 0.2f) { _hoverT = target; _anim.Stop(); }
            Invalidate();
        };
    }

    public override Size GetPreferredSize(Size proposedSize)
    {
        var t = TextRenderer.MeasureText(Text, Font);
        return new Size(t.Width + Padding.Horizontal + 6, t.Height + Padding.Vertical + 4);
    }

    protected override void OnMouseEnter(EventArgs e) { _hover = true; _anim.Start(); base.OnMouseEnter(e); }
    protected override void OnMouseLeave(EventArgs e) { _hover = false; _down = false; _anim.Start(); Invalidate(); base.OnMouseLeave(e); }
    protected override void OnMouseDown(MouseEventArgs e) { _down = true; Invalidate(); base.OnMouseDown(e); }
    protected override void OnMouseUp(MouseEventArgs e) { _down = false; Invalidate(); base.OnMouseUp(e); }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics;
        if (Parent != null) { using var b = new SolidBrush(Parent.BackColor); g.FillRectangle(b, ClientRectangle); }
        g.SmoothingMode = SmoothingMode.AntiAlias;
        var fillColor = Primary
            ? (_down ? Theme.AccentPressed : Theme.Mix(Theme.Accent, Theme.AccentHover, _hoverT))
            : (_down ? Theme.ButtonPressed : Theme.Mix(Theme.Button, Theme.ButtonHover, _hoverT));
        var rect = new RectangleF(0.5f, 0.5f, Width - 1.5f, Height - 1.5f);
        using var path = Shapes.Round(rect, 5f * DeviceDpi / 96f);
        using var fill = new SolidBrush(fillColor);
        g.FillPath(fill, path);
        if (!Primary)
        {
            using var pen = new Pen(Theme.ButtonBorder);
            g.DrawPath(pen, path);
        }
        TextRenderer.DrawText(g, Text, Font, ClientRectangle, Primary ? Theme.OnAccent : Theme.Text,
            TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine);
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing) _anim.Dispose();
        base.Dispose(disposing);
    }
}

/// <summary>Status light with soft rings that pulse outwards.</summary>
internal sealed class Dot : Control
{
    private Color _fill = Color.Gray;
    private float _phase, _intensity;

    public Dot()
    {
        SetStyle(ControlStyles.SupportsTransparentBackColor | ControlStyles.OptimizedDoubleBuffer, true);
        BackColor = Color.Transparent;
        Size = new Size(44, 44);
    }

    public void Set(Color fill, float phase, float intensity)
    {
        if (fill == _fill && phase == _phase && intensity == _intensity) return;
        _fill = fill; _phase = phase; _intensity = intensity;
        Invalidate();
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        var s = DeviceDpi / 96f;
        float cx = Width / 2f, cy = Height / 2f, core = 6f * s, max = Width / 2f - 1f;

        for (var k = 0; k < 2; k++)
        {
            var ph = (_phase + k * 0.5f) % 1f;
            var r = core + (max - core) * ph;
            using var b = new SolidBrush(Theme.Alpha(_fill, (int)(115 * _intensity * MathF.Pow(1f - ph, 1.6f))));
            g.FillEllipse(b, cx - r, cy - r, r * 2, r * 2);
        }

        var halo = core + 3.5f * s;
        using (var b = new SolidBrush(Theme.Alpha(_fill, 55)))
            g.FillEllipse(b, cx - halo, cy - halo, halo * 2, halo * 2);
        using (var b = new SolidBrush(_fill))
            g.FillEllipse(b, cx - core, cy - core, core * 2, core * 2);
    }
}

/// <summary>Schematic gamepad that lights up with the phone's current input.</summary>
internal sealed class InputView : Control
{
    private const float CanvasW = 432f, CanvasH = 168f;

    private static readonly ushort[] Masks =
    {
        Btn.A, Btn.B, Btn.X, Btn.Y, Btn.LShoulder, Btn.RShoulder, Btn.Up, Btn.Down, Btn.Left, Btn.Right,
        Btn.Start, Btn.Back, Btn.Guide, Btn.LThumb, Btn.RThumb,
    };
    private const int A = 0, B = 1, X = 2, Y = 3, LB = 4, RB = 5, DUp = 6, DDown = 7, DLeft = 8, DRight = 9,
        Menu = 10, View = 11, Guide = 12, L3 = 13, R3 = 14;

    private static readonly Font Label = new(Theme.BodyName, 10f, FontStyle.Bold, GraphicsUnit.Pixel);
    private static readonly Font Small = new(Theme.BodyName, 8.5f, FontStyle.Bold, GraphicsUnit.Pixel);
    private static readonly StringFormat Center = new() { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };

    private readonly float[] _glow = new float[Masks.Length];
    private float _lx, _ly, _rx, _ry, _lt, _rt;

    public InputView()
    {
        SetStyle(ControlStyles.SupportsTransparentBackColor | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
        BackColor = Color.Transparent;
        Size = new Size((int)CanvasW, (int)CanvasH);
    }

    /// <summary>Eases the drawn state towards <paramref name="s"/>; repaints only when something moved.</summary>
    public void Step(float dt, PadState s)
    {
        var changed = false;
        var kb = 1f - MathF.Exp(-dt * 20f);
        for (var i = 0; i < Masks.Length; i++)
            changed |= Ease(ref _glow[i], (s.Buttons & Masks[i]) != 0 ? 1f : 0f, kb);
        var ka = 1f - MathF.Exp(-dt * 28f);
        changed |= Ease(ref _lx, s.LX / 32768f, ka);
        changed |= Ease(ref _ly, s.LY / 32768f, ka);
        changed |= Ease(ref _rx, s.RX / 32768f, ka);
        changed |= Ease(ref _ry, s.RY / 32768f, ka);
        changed |= Ease(ref _lt, s.LeftTrigger / 255f, ka);
        changed |= Ease(ref _rt, s.RightTrigger / 255f, ka);
        if (changed) Invalidate();
    }

    private static bool Ease(ref float v, float target, float k)
    {
        var old = v;
        v += (target - v) * k;
        if (MathF.Abs(target - v) < 0.01f) v = target;
        return v != old;
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
        var s = Width / CanvasW;
        g.ScaleTransform(s, s);

        Trigger(g, "LT", 24, 0, _lt, false);
        Trigger(g, "RT", 238, 0, _rt, true);
        Pill(g, new RectangleF(0, 22, 84, 26), _glow[LB], "LB");
        Pill(g, new RectangleF(348, 22, 84, 26), _glow[RB], "RB");

        Stick(g, 52, 118, 40, _lx, _ly, _glow[L3]);
        DPad(g, 150, 126);
        Circle(g, 216, 80, 13, Theme.Brand, _glow[Guide], "X");
        Pill(g, new RectangleF(174, 98, 40, 20), _glow[View], "View", Small);
        Pill(g, new RectangleF(218, 98, 40, 20), _glow[Menu], "Menu", Small);
        Stick(g, 304, 126, 32, _rx, _ry, _glow[R3]);

        Circle(g, 381, 84, 13, Theme.FaceY, _glow[Y], "Y");
        Circle(g, 381, 140, 13, Theme.FaceA, _glow[A], "A");
        Circle(g, 353, 112, 13, Theme.FaceX, _glow[X], "X");
        Circle(g, 409, 112, 13, Theme.FaceB, _glow[B], "B");
    }

    private static Color Off => Theme.Mix(Theme.Card, Theme.Text, Theme.Dark ? 0.10f : 0.07f);
    private static Color Line => Theme.Mix(Theme.Card, Theme.Text, Theme.Dark ? 0.22f : 0.16f);

    private static void Pill(Graphics g, RectangleF r, float glow, string text, Font? font = null)
    {
        using var path = Shapes.Round(r, r.Height / 2f);
        using var fill = new SolidBrush(Theme.Mix(Off, Theme.Brand, glow));
        g.FillPath(fill, path);
        using var pen = new Pen(Theme.Mix(Line, Theme.Brand, glow));
        g.DrawPath(pen, path);
        using var tb = new SolidBrush(Theme.Mix(Theme.SubText, Theme.OnBrand, glow));
        g.DrawString(text, font ?? Label, tb, r, Center);
    }

    private static void Circle(Graphics g, float cx, float cy, float r, Color color, float glow, string text)
    {
        var box = new RectangleF(cx - r, cy - r, r * 2, r * 2);
        if (glow > 0.01f)
        {
            using var halo = new SolidBrush(Theme.Alpha(color, (int)(70 * glow)));
            g.FillEllipse(halo, RectangleF.Inflate(box, 4 * glow, 4 * glow));
        }
        using var fill = new SolidBrush(Theme.Mix(Off, color, glow));
        g.FillEllipse(fill, box);
        using var pen = new Pen(Theme.Mix(Line, color, Math.Max(glow, 0.55f)), 1.5f);
        g.DrawEllipse(pen, box);
        using var tb = new SolidBrush(Theme.Mix(color, Theme.OnBrand, glow));
        g.DrawString(text, Label, tb, box, Center);
    }

    private void DPad(Graphics g, float cx, float cy)
    {
        var arms = new[]
        {
            (DUp, new RectangleF(cx - 9, cy - 29, 18, 20)), (DDown, new RectangleF(cx - 9, cy + 9, 18, 20)),
            (DLeft, new RectangleF(cx - 29, cy - 9, 20, 18)), (DRight, new RectangleF(cx + 9, cy - 9, 20, 18)),
        };
        using (var hub = Shapes.Round(new RectangleF(cx - 10, cy - 10, 20, 20), 3))
        using (var b = new SolidBrush(Off))
            g.FillPath(b, hub);
        foreach (var (i, r) in arms)
        {
            using var path = Shapes.Round(r, 4);
            using var fill = new SolidBrush(Theme.Mix(Off, Theme.Brand, _glow[i]));
            g.FillPath(fill, path);
            using var pen = new Pen(Theme.Mix(Line, Theme.Brand, _glow[i]));
            g.DrawPath(pen, path);
        }
    }

    private static void Stick(Graphics g, float cx, float cy, float r, float x, float y, float click)
    {
        var box = new RectangleF(cx - r, cy - r, r * 2, r * 2);
        using (var well = new SolidBrush(Off)) g.FillEllipse(well, box);
        using (var pen = new Pen(Theme.Mix(Line, Theme.Brand, click), 1.5f)) g.DrawEllipse(pen, box);

        // Clamp to the unit circle so diagonals do not leave the well.
        var mag = MathF.Sqrt(x * x + y * y);
        if (mag > 1f) { x /= mag; y /= mag; mag = 1f; }
        const float knob = 13f;
        var travel = r - knob + 2f;
        float kx = cx + x * travel, ky = cy - y * travel;
        var active = Math.Max(click, Math.Min(1f, mag * 1.6f));
        var k = new RectangleF(kx - knob, ky - knob, knob * 2, knob * 2);
        if (active > 0.01f)
        {
            using var halo = new SolidBrush(Theme.Alpha(Theme.Brand, (int)(60 * active)));
            g.FillEllipse(halo, RectangleF.Inflate(k, 4 * active, 4 * active));
        }
        using var fill = new SolidBrush(Theme.Mix(Theme.Mix(Off, Theme.Text, 0.28f), Theme.Brand, active));
        g.FillEllipse(fill, k);
    }

    private static void Trigger(Graphics g, string label, float x, float y, float value, bool labelRight)
    {
        const float w = 170f, h = 8f;
        var bar = new RectangleF(x, y + 1, w, h);
        using (var path = Shapes.Round(bar, h / 2f))
        using (var b = new SolidBrush(Off))
            g.FillPath(b, path);
        if (value > 0.01f)
        {
            using var path = Shapes.Round(new RectangleF(x, y + 1, Math.Max(h, w * value), h), h / 2f);
            using var b = new SolidBrush(Theme.Brand);
            g.FillPath(b, path);
        }
        var text = new RectangleF(labelRight ? x + w + 4 : x - 24, y - 3, 20, 16);
        using var tb = new SolidBrush(Theme.Mix(Theme.SubText, Theme.Brand, value));
        g.DrawString(label, Small, tb, text, new StringFormat { Alignment = labelRight ? StringAlignment.Near : StringAlignment.Far, LineAlignment = StringAlignment.Center });
    }
}
