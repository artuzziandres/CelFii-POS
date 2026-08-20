using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;

namespace CelFiiVentas.Windows;

public static class RawPrinter
{
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private sealed class DOCINFO { public string pDocName = "Cel-Fii Ticket"; public string? pOutputFile; public string pDataType = "RAW"; }
    [DllImport("winspool.drv", SetLastError = true, CharSet = CharSet.Unicode)] private static extern bool OpenPrinter(string name, out IntPtr handle, IntPtr defaults);
    [DllImport("winspool.drv", SetLastError = true)] private static extern bool ClosePrinter(IntPtr handle);
    [DllImport("winspool.drv", SetLastError = true, CharSet = CharSet.Unicode)] private static extern int StartDocPrinter(IntPtr handle, int level, [In] DOCINFO info);
    [DllImport("winspool.drv", SetLastError = true)] private static extern bool EndDocPrinter(IntPtr handle);
    [DllImport("winspool.drv", SetLastError = true)] private static extern bool StartPagePrinter(IntPtr handle);
    [DllImport("winspool.drv", SetLastError = true)] private static extern bool EndPagePrinter(IntPtr handle);
    [DllImport("winspool.drv", SetLastError = true)] private static extern bool WritePrinter(IntPtr handle, byte[] bytes, int count, out int written);

    public static void Print(string printerName, byte[] bytes)
    {
        if (string.IsNullOrWhiteSpace(printerName)) throw new InvalidOperationException("Seleccioná la impresora en Más.");
        if (!OpenPrinter(printerName, out var h, IntPtr.Zero)) throw new Win32Exception(Marshal.GetLastWin32Error());
        try
        {
            if (StartDocPrinter(h, 1, new DOCINFO()) == 0 || !StartPagePrinter(h)) throw new Win32Exception(Marshal.GetLastWin32Error());
            try { if (!WritePrinter(h, bytes, bytes.Length, out var n) || n != bytes.Length) throw new Win32Exception(Marshal.GetLastWin32Error()); }
            finally { EndPagePrinter(h); EndDocPrinter(h); }
        }
        finally { ClosePrinter(h); }
    }

    public static byte[] Ticket(string saleId, string seller, string payment, IEnumerable<CartLine> lines)
    {
        Encoding.RegisterProvider(CodePagesEncodingProvider.Instance);
        var enc = Encoding.GetEncoding(850);
        var text = new StringBuilder();
        text.AppendLine("       CEL-FII TECNOLOGIA").AppendLine("       ARTINA - CERRO").AppendLine(new string('-', 32));
        text.AppendLine(DateTime.Now.ToString("dd/MM/yyyy HH:mm")).AppendLine("Venta: " + saleId).AppendLine("Vendedor: " + seller).AppendLine(new string('-', 32));
        foreach (var l in lines) text.AppendLine(l.Product.Name).AppendLine($"{l.Quantity} x {l.UnitPrice:C0}        {l.Total:C0}");
        text.AppendLine(new string('-', 32)).AppendLine($"TOTAL: {lines.Sum(x => x.Total):C0}").AppendLine("Pago: " + payment);
        text.AppendLine().AppendLine("        Gracias por su compra").AppendLine().AppendLine().AppendLine();
        return [0x1B, 0x40, .. enc.GetBytes(text.ToString()), 0x1D, 0x56, 0x00];
    }
}
