namespace HypeXboxServer;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        using var single = new Mutex(true, "HypeXboxServer.SingleInstance", out var isFirst);
        if (!isFirst)
        {
            MessageBox.Show("hype.xbox is already running.", "hype.xbox", MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }
        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm());
    }
}
