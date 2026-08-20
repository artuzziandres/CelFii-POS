namespace CelFiiVentas.Windows;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        try
        {
            ApplicationConfiguration.Initialize();
            Application.Run(new MainForm());
        }
        catch (Exception ex)
        {
            var log = Path.Combine(Path.GetTempPath(), "CelFii-Ventas-error.txt");
            File.WriteAllText(log, ex.ToString());
            MessageBox.Show($"No se pudo iniciar Cel-Fii Ventas.\n\n{ex.Message}\n\nInforme guardado en:\n{log}",
                "Cel-Fii Ventas", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }
}
