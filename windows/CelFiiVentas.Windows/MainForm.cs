using System.Drawing.Printing;

namespace CelFiiVentas.Windows;

public sealed class MainForm : Form
{
    private static readonly Color Lime = Color.FromArgb(157, 255, 0);
    private static readonly Color Ink = Color.FromArgb(7, 10, 8);
    private static readonly Color PanelColor = Color.FromArgb(22, 26, 22);
    private readonly AppSettings _settings = SettingsStore.Load();
    private readonly CelFiiApi _api;
    private readonly TabControl _tabs = new() { Dock = DockStyle.Fill, Appearance = TabAppearance.FlatButtons };
    private readonly TextBox _search = new() { PlaceholderText = "Buscar nombre, modelo o código de barras" };
    private readonly DataGridView _products = Grid();
    private readonly DataGridView _cart = Grid();
    private readonly DataGridView _productAdmin = Grid();
    private readonly TreeView _history = new() { Dock = DockStyle.Fill, BackColor = Ink, ForeColor = Color.White, Font = new("Segoe UI", 11), BorderStyle = BorderStyle.None };
    private readonly ComboBox _seller = new() { DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly ComboBox _payment = new() { DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly Label _total = new() { AutoSize = true, ForeColor = Lime, Font = new("Segoe UI", 24, FontStyle.Bold), Text = "$ 0" };
    private readonly Label _status = new() { AutoSize = true, ForeColor = Color.Silver, Text = "Sin sincronizar" };
    private List<Product> _allProducts = [];
    private readonly List<CartLine> _cartLines = [];

    public MainForm()
    {
        _api = new(() => _settings);
        Text = "Cel-Fii Ventas";
        MinimumSize = new Size(1100, 720);
        WindowState = FormWindowState.Maximized;
        BackColor = Ink;
        ForeColor = Color.White;
        Font = new Font("Segoe UI", 10);
        Controls.Add(_tabs);
        Controls.Add(BuildHeader());
        BuildSaleTab();
        BuildProductsTab();
        BuildHistoryTab();
        BuildMoreTab();
        Shown += async (_, _) => await ReloadAllAsync();
    }

    private Control BuildHeader()
    {
        var header = new Panel { Dock = DockStyle.Top, Height = 86, BackColor = Color.Black, Padding = new Padding(22, 10, 22, 8) };
        var title = new Label { Text = "CEL-FII", ForeColor = Lime, Font = new("Segoe UI", 25, FontStyle.Bold), AutoSize = true, Location = new(105, 12) };
        var sub = new Label { Text = "VENTAS · WINDOWS", ForeColor = Color.Gray, Font = new("Segoe UI", 10, FontStyle.Bold), AutoSize = true, Location = new(108, 55) };
        var logoPath = Path.Combine(AppContext.BaseDirectory, "logo_celfii_app.png");
        if (File.Exists(logoPath)) header.Controls.Add(new PictureBox { Image = Image.FromFile(logoPath), SizeMode = PictureBoxSizeMode.Zoom, Bounds = new(14, 4, 82, 76) });
        _status.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        _status.Location = new(header.Width - 240, 32);
        header.Resize += (_, _) => _status.Left = header.ClientSize.Width - _status.Width - 24;
        header.Controls.AddRange([title, sub, _status]);
        return header;
    }

    private void BuildSaleTab()
    {
        var tab = NewTab("VENTA");
        var split = new SplitContainer { Dock = DockStyle.Fill, SplitterDistance = 690, BackColor = Ink };
        tab.Controls.Add(split);
        var left = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 2, Padding = new Padding(18), BackColor = Ink };
        left.RowStyles.Add(new RowStyle(SizeType.Absolute, 58)); left.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        _search.Dock = DockStyle.Fill; StyleInput(_search); _search.TextChanged += (_, _) => FilterProducts();
        _search.KeyDown += (_, e) => { if (e.KeyCode == Keys.Enter) { AddExactCode(); e.SuppressKeyPress = true; } };
        left.Controls.Add(_search, 0, 0); left.Controls.Add(_products, 0, 1);
        split.Panel1.Controls.Add(left);
        _products.CellDoubleClick += (_, e) => { if (e.RowIndex >= 0) AddProduct((Product)_products.Rows[e.RowIndex].Tag); };

        var right = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 7, Padding = new Padding(18), BackColor = PanelColor };
        right.RowStyles.Add(new RowStyle(SizeType.Absolute, 48)); right.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        right.RowStyles.Add(new RowStyle(SizeType.Absolute, 48)); right.RowStyles.Add(new RowStyle(SizeType.Absolute, 48));
        right.RowStyles.Add(new RowStyle(SizeType.Absolute, 58)); right.RowStyles.Add(new RowStyle(SizeType.Absolute, 58)); right.RowStyles.Add(new RowStyle(SizeType.Absolute, 70));
        right.Controls.Add(new Label { Text = "TICKET", ForeColor = Lime, Font = new("Segoe UI", 18, FontStyle.Bold), AutoSize = true }, 0, 0);
        right.Controls.Add(_cart, 0, 1); right.Controls.Add(LabelFor("Vendedor"), 0, 2); right.Controls.Add(_seller, 0, 3);
        right.Controls.Add(LabelFor("Forma de pago"), 0, 4); right.Controls.Add(_payment, 0, 5);
        var actions = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.RightToLeft };
        var print = ActionButton("GUARDAR E IMPRIMIR", async (_, _) => await FinishSaleAsync(true));
        var save = ActionButton("SOLO GUARDAR", async (_, _) => await FinishSaleAsync(false));
        actions.Controls.AddRange([print, save, _total]); right.Controls.Add(actions, 0, 6);
        split.Panel2.Controls.Add(right);
        _seller.Items.AddRange(["Andres", "Maxi", "Gaby", "Facu", "Malena", "Benjamin", "Alejandra", "Elio"]); _seller.SelectedIndex = 0;
        _payment.Items.AddRange(["Efectivo", "Transferencia", "Posnet"]); _payment.SelectedIndex = 0;
    }

    private void BuildProductsTab()
    {
        var tab = NewTab("PRODUCTOS");
        var panel = new Panel { Dock = DockStyle.Fill, Padding = new Padding(18), BackColor = Ink };
        var bar = new FlowLayoutPanel { Dock = DockStyle.Top, Height = 62 };
        bar.Controls.Add(ActionButton("NUEVO PRODUCTO", (_, _) => EditProduct(null)));
        bar.Controls.Add(ActionButton("EDITAR SELECCIONADO", (_, _) => { if (_productAdmin.CurrentRow?.Tag is Product p) EditProduct(p); }));
        bar.Controls.Add(ActionButton("ACTUALIZAR", async (_, _) => await ReloadProductsAsync()));
        panel.Controls.Add(_productAdmin); panel.Controls.Add(bar); tab.Controls.Add(panel);
        _productAdmin.CellDoubleClick += (_, e) => { if (e.RowIndex >= 0) EditProduct((Product)_productAdmin.Rows[e.RowIndex].Tag); };
    }

    private void BuildHistoryTab()
    {
        var tab = NewTab("HISTORIAL");
        var panel = new Panel { Dock = DockStyle.Fill, Padding = new Padding(18), BackColor = Ink };
        var refresh = ActionButton("SINCRONIZAR HISTORIAL", async (_, _) => await ReloadHistoryAsync()); refresh.Dock = DockStyle.Top;
        panel.Controls.Add(_history); panel.Controls.Add(refresh); tab.Controls.Add(panel);
        _history.NodeMouseDoubleClick += (_, e) => { if (e.Node.Tag is Sale s) ShowSale(s); };
    }

    private void BuildMoreTab()
    {
        var tab = NewTab("MÁS");
        var panel = new TableLayoutPanel { Dock = DockStyle.Top, Width = 850, Height = 360, Padding = new Padding(28), RowCount = 7, BackColor = Ink };
        var url = new TextBox { Text = _settings.ApiUrl, Dock = DockStyle.Fill }; StyleInput(url);
        var token = new TextBox { Text = _settings.Token, UseSystemPasswordChar = true, Dock = DockStyle.Fill }; StyleInput(token);
        var printers = new ComboBox { Dock = DockStyle.Fill, DropDownStyle = ComboBoxStyle.DropDownList };
        foreach (string name in PrinterSettings.InstalledPrinters) printers.Items.Add(name);
        if (printers.Items.Contains(_settings.PrinterName)) printers.SelectedItem = _settings.PrinterName; else if (printers.Items.Count > 0) printers.SelectedIndex = 0;
        panel.Controls.Add(LabelFor("URL del conector Google Sheets"), 0, 0); panel.Controls.Add(url, 0, 1);
        panel.Controls.Add(LabelFor("Token privado"), 0, 2); panel.Controls.Add(token, 0, 3);
        panel.Controls.Add(LabelFor("Impresora USB instalada en Windows"), 0, 4); panel.Controls.Add(printers, 0, 5);
        panel.Controls.Add(ActionButton("GUARDAR Y PROBAR", async (_, _) => { _settings.ApiUrl = url.Text.Trim(); _settings.Token = token.Text.Trim(); _settings.PrinterName = printers.Text; SettingsStore.Save(_settings); await RunBusy(async () => { await _api.TestAsync(); MessageBox.Show("Conexión correcta.", "Cel-Fii"); }); }), 0, 6);
        tab.Controls.Add(panel);
    }

    private async Task ReloadAllAsync() { await ReloadProductsAsync(); await ReloadHistoryAsync(); }
    private async Task ReloadProductsAsync() => await RunBusy(async () => { _allProducts = await _api.GetProductsAsync(); FilterProducts(); FillProductAdmin(); _status.Text = $"● {_allProducts.Count} PRODUCTOS"; });
    private async Task ReloadHistoryAsync() => await RunBusy(async () => { var sales = await _api.GetSalesAsync(); _history.Nodes.Clear(); foreach (var group in sales.GroupBy(s => s.Month).OrderByDescending(g => g.Key)) { var month = _history.Nodes.Add(group.Key); foreach (var s in group) { var node = month.Nodes.Add($"{s.Date} · {s.Seller} · {s.Total:C0} · {s.Payment}"); node.Tag = s; } } if (_history.Nodes.Count > 0) _history.Nodes[0].Expand(); });

    private void FilterProducts()
    {
        var q = _search.Text.Trim(); var list = string.IsNullOrEmpty(q) ? _allProducts : _allProducts.Where(p => (p.Name + " " + p.Category + " " + p.Code + " " + p.BackupCode).Contains(q, StringComparison.OrdinalIgnoreCase)).ToList();
        FillProductsGrid(_products, list);
    }
    private void FillProductAdmin() => FillProductsGrid(_productAdmin, _allProducts);
    private static void FillProductsGrid(DataGridView grid, IEnumerable<Product> products)
    {
        grid.Rows.Clear(); grid.Columns.Clear(); grid.Columns.Add("name", "Producto"); grid.Columns.Add("category", "Categoría"); grid.Columns.Add("code", "Código"); grid.Columns.Add("price", "Efectivo"); grid.Columns.Add("card", "Posnet"); grid.Columns.Add("stock", "Stock");
        grid.Columns[0].AutoSizeMode = DataGridViewAutoSizeColumnMode.Fill;
        foreach (var p in products) { var i = grid.Rows.Add(p.Name, p.Category, p.Code, p.CashPrice.ToString("C0"), p.CardPrice.ToString("C0"), p.Stock); grid.Rows[i].Tag = p; }
    }
    private void AddExactCode() { var p = _allProducts.FirstOrDefault(x => x.Code.Equals(_search.Text.Trim(), StringComparison.OrdinalIgnoreCase) || x.BackupCode.Equals(_search.Text.Trim(), StringComparison.OrdinalIgnoreCase)); if (p != null) { AddProduct(p); _search.Clear(); } }
    private void AddProduct(Product product) { var line = _cartLines.FirstOrDefault(x => x.Product.Id == product.Id); if (line == null) _cartLines.Add(new CartLine { Product = product, Quantity = 1, UnitPrice = _payment.Text == "Posnet" && product.CardPrice > 0 ? product.CardPrice : product.CashPrice }); else if (line.Quantity < product.Stock) line.Quantity++; RefreshCart(); }
    private void RefreshCart() { _cart.Rows.Clear(); _cart.Columns.Clear(); _cart.Columns.Add("product", "Producto"); _cart.Columns.Add("qty", "Cant."); _cart.Columns.Add("price", "Precio"); _cart.Columns.Add("total", "Total"); _cart.Columns[0].AutoSizeMode = DataGridViewAutoSizeColumnMode.Fill; foreach (var x in _cartLines) _cart.Rows.Add(x.Product.Name, x.Quantity, x.UnitPrice.ToString("C0"), x.Total.ToString("C0")); _total.Text = _cartLines.Sum(x => x.Total).ToString("C0"); }

    private async Task FinishSaleAsync(bool print)
    {
        if (_cartLines.Count == 0) { MessageBox.Show("Agregá productos al ticket."); return; }
        await RunBusy(async () => { var id = await _api.CreateSaleAsync(_cartLines, _seller.Text, _payment.Text); if (print) RawPrinter.Print(_settings.PrinterName, RawPrinter.Ticket(id, _seller.Text, _payment.Text, _cartLines)); MessageBox.Show(print ? "Venta guardada e impresa." : "Venta guardada.", "Cel-Fii"); _cartLines.Clear(); RefreshCart(); await ReloadAllAsync(); });
    }

    private void EditProduct(Product? original)
    {
        var creating = original is null; var p = original is null ? new Product() : new Product { Id = original.Id, Name = original.Name, Category = original.Category, CashPrice = original.CashPrice, CardPrice = original.CardPrice, Stock = original.Stock, Code = original.Code, BackupCode = original.BackupCode, Photo = original.Photo, Description = original.Description, Type = original.Type, MinimumStock = original.MinimumStock, CostUsd = original.CostUsd };
        using var dialog = new ProductDialog(p, creating);
        if (dialog.ShowDialog(this) == DialogResult.OK) _ = RunBusy(async () => { await _api.SaveProductAsync(dialog.Product, creating); await ReloadProductsAsync(); });
    }
    private void ShowSale(Sale s) { var details = string.Join(Environment.NewLine, s.Details.Select(x => $"{x.Quantity} x {x.Name}  {x.Total:C0}")); MessageBox.Show($"Venta {s.Id}\n{s.Date}\nVendedor: {s.Seller}\nPago: {s.Payment}\n\n{details}\n\nTOTAL: {s.Total:C0}", "Detalle de venta"); }
    private async Task RunBusy(Func<Task> action) { try { UseWaitCursor = true; Enabled = false; await action(); } catch (Exception ex) { MessageBox.Show(ex.Message, "Cel-Fii Ventas", MessageBoxButtons.OK, MessageBoxIcon.Error); } finally { Enabled = true; UseWaitCursor = false; } }

    private TabPage NewTab(string text) { var tab = new TabPage(text) { BackColor = Ink, ForeColor = Color.White }; _tabs.TabPages.Add(tab); return tab; }
    private static DataGridView Grid() => new() { Dock = DockStyle.Fill, ReadOnly = true, AllowUserToAddRows = false, AllowUserToDeleteRows = false, SelectionMode = DataGridViewSelectionMode.FullRowSelect, MultiSelect = false, BackgroundColor = Ink, GridColor = Color.FromArgb(45, 50, 45), ForeColor = Color.White, RowHeadersVisible = false, AutoSizeRowsMode = DataGridViewAutoSizeRowsMode.AllCells, BorderStyle = BorderStyle.None, DefaultCellStyle = new DataGridViewCellStyle { BackColor = PanelColor, ForeColor = Color.White, SelectionBackColor = Color.FromArgb(65, 90, 35), SelectionForeColor = Color.White, Padding = new Padding(6) }, ColumnHeadersDefaultCellStyle = new DataGridViewCellStyle { BackColor = Color.Black, ForeColor = Lime, Font = new Font("Segoe UI", 10, FontStyle.Bold) }, EnableHeadersVisualStyles = false };
    private static Button ActionButton(string text, EventHandler action) { var b = new Button { Text = text, AutoSize = true, Height = 46, BackColor = Lime, ForeColor = Color.Black, FlatStyle = FlatStyle.Flat, Font = new("Segoe UI", 10, FontStyle.Bold), Margin = new Padding(6) }; b.FlatAppearance.BorderSize = 0; b.Click += action; return b; }
    private static Label LabelFor(string text) => new() { Text = text, ForeColor = Color.White, Font = new("Segoe UI", 11, FontStyle.Bold), AutoSize = true, Padding = new Padding(0, 10, 0, 0) };
    private static void StyleInput(TextBox text) { text.BackColor = PanelColor; text.ForeColor = Color.White; text.BorderStyle = BorderStyle.FixedSingle; text.Font = new("Segoe UI", 13); }
}

internal sealed class ProductDialog : Form
{
    public Product Product { get; }
    public ProductDialog(Product product, bool creating)
    {
        Product = product; Text = creating ? "Nuevo producto" : "Editar producto"; Width = 570; Height = 650; StartPosition = FormStartPosition.CenterParent; BackColor = Color.FromArgb(18, 22, 18); ForeColor = Color.White;
        var fields = new (string Label, Func<string> Get, Action<string> Set)[] {
            ("Nombre", () => product.Name, v => product.Name = v), ("Categoría", () => product.Category, v => product.Category = v),
            ("Precio efectivo", () => product.CashPrice.ToString(), v => product.CashPrice = Decimal(v)), ("Precio Posnet", () => product.CardPrice.ToString(), v => product.CardPrice = Decimal(v)),
            ("Stock inicial / actual", () => product.Stock.ToString(), v => product.Stock = Integer(v)), ("Código de barras", () => product.Code, v => product.Code = v),
            ("Código alternativo", () => product.BackupCode, v => product.BackupCode = v), ("Foto (URL de Drive)", () => product.Photo, v => product.Photo = v),
            ("Descripción", () => product.Description, v => product.Description = v)
        };
        var layout = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(18), RowCount = fields.Length * 2 + 1, AutoScroll = true };
        foreach (var f in fields) { layout.Controls.Add(new Label { Text = f.Label, AutoSize = true, ForeColor = Color.FromArgb(157,255,0), Padding = new Padding(0,6,0,2) }); var input = new TextBox { Text = f.Get(), Dock = DockStyle.Top, BackColor = Color.FromArgb(30,34,30), ForeColor = Color.White, Font = new("Segoe UI", 11) }; input.TextChanged += (_, _) => f.Set(input.Text); layout.Controls.Add(input); }
        var save = new Button { Text = "GUARDAR", DialogResult = DialogResult.OK, Height = 46, Dock = DockStyle.Top, BackColor = Color.FromArgb(157,255,0), FlatStyle = FlatStyle.Flat, Font = new("Segoe UI", 10, FontStyle.Bold) }; layout.Controls.Add(save); Controls.Add(layout); AcceptButton = save;
    }
    private static decimal Decimal(string value) => decimal.TryParse(value, out var n) ? n : 0;
    private static int Integer(string value) => int.TryParse(value, out var n) ? n : 0;
}
