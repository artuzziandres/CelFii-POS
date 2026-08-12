using System.Drawing.Printing;

namespace CelFiiVentas.Windows;

public sealed class MainForm : Form
{
    private static readonly Color Lime = Color.FromArgb(157, 255, 0);
    private static readonly Color Ink = Color.FromArgb(7, 10, 8);
    private static readonly Color PanelColor = Color.FromArgb(22, 26, 22);
    private readonly AppSettings _settings = SettingsStore.Load();
    private readonly CelFiiApi _api;
    private readonly TabControl _tabs = new() { Dock = DockStyle.Fill, Appearance = TabAppearance.FlatButtons,
        DrawMode = TabDrawMode.OwnerDrawFixed, SizeMode = TabSizeMode.Fixed, ItemSize = new Size(175, 46),
        Padding = new Point(18, 7) };
    private readonly TextBox _search = new() { PlaceholderText = "Buscar nombre, modelo o código de barras" };
    private readonly DataGridView _products = Grid();
    private readonly DataGridView _cart = Grid();
    private readonly DataGridView _productAdmin = Grid();
    private readonly TreeView _history = new() { Dock = DockStyle.Fill, BackColor = Ink, ForeColor = Color.White, Font = new("Segoe UI", 11), BorderStyle = BorderStyle.None };
    private readonly ComboBox _seller = new() { DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly ComboBox _payment = new() { DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly ComboBox _saleType = new() { DropDownStyle = ComboBoxStyle.DropDownList };
    private readonly ComboBox _productType = new() { DropDownStyle = ComboBoxStyle.DropDownList };
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
        _tabs.DrawItem += DrawTab;
        Controls.Add(_tabs);
        Controls.Add(BuildHeader());
        BuildSaleTab();
        BuildProductsTab();
        BuildHistoryTab();
        BuildMoreTab();
        Shown += async (_, _) => await ReloadAllAsync();
    }

    private void DrawTab(object? sender, DrawItemEventArgs e)
    {
        var selected = e.Index == _tabs.SelectedIndex;
        using var background = new SolidBrush(selected ? Lime : Color.FromArgb(20, 24, 20));
        using var foreground = new SolidBrush(selected ? Color.Black : Color.White);
        e.Graphics.FillRectangle(background, e.Bounds);
        TextRenderer.DrawText(e.Graphics, _tabs.TabPages[e.Index].Text,
            new Font("Segoe UI", 10, FontStyle.Bold), e.Bounds, foreground.Color,
            TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
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
        var split = new SplitContainer { Dock = DockStyle.Fill, SplitterWidth = 8,
            Panel1MinSize = 600, Panel2MinSize = 430, FixedPanel = FixedPanel.Panel2,
            BackColor = Color.FromArgb(40, 45, 40) };
        split.Resize += (_, _) => { if (split.ClientSize.Width > 1050)
            split.SplitterDistance = Math.Max(600, split.ClientSize.Width - 450); };
        tab.Controls.Add(split);
        var left = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 3, Padding = new Padding(18), BackColor = Ink };
        left.RowStyles.Add(new RowStyle(SizeType.Absolute, 48)); left.RowStyles.Add(new RowStyle(SizeType.Absolute, 58)); left.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        _saleType.Items.AddRange(["Todos", "Accesorios", "Repuestos", "Equipos"]); _saleType.SelectedIndex = 0;
        _saleType.Dock = DockStyle.Fill; _saleType.SelectedIndexChanged += (_, _) => FilterProducts();
        _search.Dock = DockStyle.Fill; StyleInput(_search); _search.TextChanged += (_, _) => FilterProducts();
        _search.KeyDown += (_, e) => { if (e.KeyCode == Keys.Enter) { AddExactCode(); e.SuppressKeyPress = true; } };
        left.Controls.Add(_saleType, 0, 0); left.Controls.Add(_search, 0, 1); left.Controls.Add(_products, 0, 2);
        split.Panel1.Controls.Add(left);
        _products.CellDoubleClick += (_, e) => { if (e.RowIndex >= 0) AddProduct((Product)_products.Rows[e.RowIndex].Tag); };
        _cart.CellDoubleClick += (_, e) => { if (e.RowIndex >= 0) ChangePrice(e.RowIndex); };

        var right = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 7,
            Padding = new Padding(22, 18, 22, 18), BackColor = PanelColor };
        right.RowStyles.Add(new RowStyle(SizeType.Absolute, 48)); right.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        right.RowStyles.Add(new RowStyle(SizeType.Absolute, 48)); right.RowStyles.Add(new RowStyle(SizeType.Absolute, 48));
        right.RowStyles.Add(new RowStyle(SizeType.Absolute, 52)); right.RowStyles.Add(new RowStyle(SizeType.Absolute, 52)); right.RowStyles.Add(new RowStyle(SizeType.Absolute, 112));
        right.Controls.Add(new Label { Text = "TICKET", ForeColor = Lime, Font = new("Segoe UI", 18, FontStyle.Bold), AutoSize = true }, 0, 0);
        right.Controls.Add(_cart, 0, 1); right.Controls.Add(LabelFor("Vendedor"), 0, 2); right.Controls.Add(_seller, 0, 3);
        right.Controls.Add(LabelFor("Forma de pago"), 0, 4); right.Controls.Add(_payment, 0, 5);
        var actions = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 2, ColumnCount = 2,
            Padding = new Padding(0, 8, 0, 0) };
        actions.RowStyles.Add(new RowStyle(SizeType.Absolute, 42));
        actions.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        actions.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 43));
        actions.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 57));
        var print = ActionButton("GUARDAR E IMPRIMIR", async (_, _) => await FinishSaleAsync(true));
        var save = ActionButton("SOLO GUARDAR", async (_, _) => await FinishSaleAsync(false));
        _total.Dock = DockStyle.Fill; _total.TextAlign = ContentAlignment.MiddleLeft;
        actions.Controls.Add(_total, 0, 0); actions.SetColumnSpan(_total, 2);
        save.Dock = DockStyle.Fill; print.Dock = DockStyle.Fill;
        actions.Controls.Add(save, 0, 1); actions.Controls.Add(print, 1, 1);
        right.Controls.Add(actions, 0, 6);
        split.Panel2.Controls.Add(right);
        _seller.Items.AddRange(["Andres", "Maxi", "Gaby", "Facu", "Malena", "Benjamin", "Alejandra", "Elio"]); _seller.SelectedIndex = 0;
        _payment.Items.AddRange(["Efectivo", "Transferencia", "Posnet"]); _payment.SelectedIndex = 0;
    }

    private void BuildProductsTab()
    {
        var tab = NewTab("PRODUCTOS");
        var panel = new Panel { Dock = DockStyle.Fill, Padding = new Padding(18), BackColor = Ink };
        var bar = new FlowLayoutPanel { Dock = DockStyle.Top, Height = 62 };
        _productType.Items.AddRange(["Todos", "Accesorios", "Repuestos", "Equipos"]); _productType.SelectedIndex = 0;
        _productType.Width = 170; _productType.SelectedIndexChanged += (_, _) => FillProductAdmin();
        bar.Controls.Add(_productType);
        bar.Controls.Add(ActionButton("NUEVO PRODUCTO", (_, _) => ChooseProductType()));
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
        var q = _search.Text.Trim(); var list = _allProducts.Where(p => !p.IsSold && MatchesType(p, _saleType.Text));
        if (!string.IsNullOrEmpty(q)) list = list.Where(p => (p.Name + " " + p.Category + " " + p.Code + " " + p.BackupCode + " " + p.Imei + " " + p.Memory).Contains(q, StringComparison.OrdinalIgnoreCase));
        FillProductsGrid(_products, list);
    }
    private void FillProductAdmin() => FillProductsGrid(_productAdmin,
        _allProducts.Where(p => !p.IsSold && MatchesType(p, _productType.Text)));
    private static bool MatchesType(Product p, string selected) => selected == "Todos"
        || selected == "Accesorios" && (string.IsNullOrWhiteSpace(p.Type) || p.Type.StartsWith("Accesorio", StringComparison.OrdinalIgnoreCase))
        || selected == "Repuestos" && p.Type.StartsWith("Repuesto", StringComparison.OrdinalIgnoreCase)
        || selected == "Equipos" && p.Type.StartsWith("Equipo", StringComparison.OrdinalIgnoreCase);
    private static void FillProductsGrid(DataGridView grid, IEnumerable<Product> products)
    {
        grid.Rows.Clear(); grid.Columns.Clear(); grid.Columns.Add("name", "Producto"); grid.Columns.Add("type", "Tipo"); grid.Columns.Add("details", "Memoria / Color / IMEI"); grid.Columns.Add("price", "Precio"); grid.Columns.Add("status", "Estado / Stock");
        grid.Columns[0].AutoSizeMode = DataGridViewAutoSizeColumnMode.Fill;
        foreach (var p in products) { var detail = p.IsEquipment ? $"{p.Memory} · {p.Color} · {p.Imei}" : $"{p.Category} · {p.Code}"; var status = p.IsEquipment ? p.EquipmentStatus : $"Stock {p.Stock}"; var i = grid.Rows.Add(p.Name, string.IsNullOrWhiteSpace(p.Type) ? "Accesorio" : p.Type, detail, p.CashPrice.ToString("C0"), status); grid.Rows[i].Tag = p; }
    }
    private void AddExactCode() { var p = _allProducts.FirstOrDefault(x => x.Code.Equals(_search.Text.Trim(), StringComparison.OrdinalIgnoreCase) || x.BackupCode.Equals(_search.Text.Trim(), StringComparison.OrdinalIgnoreCase)); if (p != null) { AddProduct(p); _search.Clear(); } }
    private void AddProduct(Product product) { if (product.IsEquipment && string.IsNullOrWhiteSpace(product.Imei)) { MessageBox.Show("Completá el IMEI antes de vender el equipo."); return; } var line = _cartLines.FirstOrDefault(x => x.Product.Id == product.Id); if (line == null) _cartLines.Add(new CartLine { Product = product, Quantity = 1, UnitPrice = product.CashPrice }); else if (!product.IsEquipment && line.Quantity < product.Stock) line.Quantity++; RefreshCart(); }
    private void RefreshCart() { _cart.Rows.Clear(); _cart.Columns.Clear(); _cart.ScrollBars = ScrollBars.Vertical; _cart.AutoSizeColumnsMode = DataGridViewAutoSizeColumnsMode.None; _cart.Columns.Add("product", "Producto"); _cart.Columns.Add("qty", "Cant."); _cart.Columns.Add("price", "Precio"); _cart.Columns.Add("total", "Total"); _cart.Columns[0].AutoSizeMode = DataGridViewAutoSizeColumnMode.Fill; _cart.Columns[1].Width = 58; _cart.Columns[2].Width = 96; _cart.Columns[3].Width = 96; foreach (var x in _cartLines) _cart.Rows.Add(x.Product.Name, x.Quantity, x.UnitPrice.ToString("C0"), x.Total.ToString("C0")); _total.Text = _cartLines.Sum(x => x.Total).ToString("C0"); }
    private void ChangePrice(int row) { var line = _cartLines[row]; var value = Microsoft.VisualBasic.Interaction.InputBox("Precio final", line.Product.Name, line.UnitPrice.ToString("0.##")); if (decimal.TryParse(value, out var price) && price > 0) { line.UnitPrice = price; RefreshCart(); } }

    private async Task FinishSaleAsync(bool print)
    {
        if (_cartLines.Count == 0) { MessageBox.Show("Agregá productos al ticket."); return; }
        await RunBusy(async () => { var id = await _api.CreateSaleAsync(_cartLines, _seller.Text, _payment.Text); if (print) RawPrinter.Print(_settings.PrinterName, RawPrinter.Ticket(id, _seller.Text, _payment.Text, _cartLines)); MessageBox.Show(print ? "Venta guardada e impresa." : "Venta guardada.", "Cel-Fii"); _cartLines.Clear(); RefreshCart(); await ReloadAllAsync(); });
    }

    private void ChooseProductType()
    {
        using var choice = new Form { Text = "Nuevo producto", Width = 360, Height = 190, StartPosition = FormStartPosition.CenterParent, BackColor = Ink };
        var panel = new FlowLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(16) };
        foreach (var type in new[] { "Accesorio", "Repuesto", "Equipo" })
            panel.Controls.Add(ActionButton(type.ToUpperInvariant(), (_, _) => { choice.Tag = type; choice.DialogResult = DialogResult.OK; }));
        choice.Controls.Add(panel);
        if (choice.ShowDialog(this) == DialogResult.OK) EditProduct(null, (string)choice.Tag!);
    }

    private void EditProduct(Product? original, string? forcedType = null)
    {
        var creating = original is null; var p = original is null ? new Product { Type = forcedType ?? "Accesorio", Stock = forcedType == "Equipo" ? 1 : 0 } : CloneProduct(original);
        using var dialog = new ProductDialog(p, creating);
        if (dialog.ShowDialog(this) == DialogResult.OK) _ = RunBusy(async () => {
            var id = await _api.SaveProductAndReturnIdAsync(dialog.Product, creating);
            for (var i = 0; i < dialog.PhotoPaths.Length; i++)
                if (!string.IsNullOrWhiteSpace(dialog.PhotoPaths[i])) await _api.UploadPhotoAsync(id, dialog.PhotoPaths[i], i + 1);
            await ReloadProductsAsync();
        });
    }
    private static Product CloneProduct(Product p) => new() { Id=p.Id, Name=p.Name, Category=p.Category, CashPrice=p.CashPrice, CardPrice=p.CardPrice, Stock=p.Stock, Code=p.Code, BackupCode=p.BackupCode, Photo=p.Photo, Photo2=p.Photo2, Photo3=p.Photo3, Description=p.Description, Type=p.Type, MinimumStock=p.MinimumStock, CostUsd=p.CostUsd, Cost=p.Cost, Brand=p.Brand, CompatibleModels=p.CompatibleModels, Color=p.Color, Supplier=p.Supplier, Quality=p.Quality, WarrantyInfo=p.WarrantyInfo, Imei=p.Imei, Memory=p.Memory, Condition=p.Condition, Battery=p.Battery, Observations=p.Observations, EquipmentStatus=p.EquipmentStatus, ReservationCustomer=p.ReservationCustomer, ReservationPhone=p.ReservationPhone, ReservationDeposit=p.ReservationDeposit, ReservationDate=p.ReservationDate, ReservationExpiry=p.ReservationExpiry };
    private void ShowSale(Sale s) { var details = string.Join(Environment.NewLine, s.Details.Select(x => $"{x.Quantity} x {x.Name}  {x.Total:C0}")); MessageBox.Show($"Venta {s.Id}\n{s.Date}\nVendedor: {s.Seller}\nPago: {s.Payment}\n\n{details}\n\nTOTAL: {s.Total:C0}", "Detalle de venta"); }
    private async Task RunBusy(Func<Task> action) { try { UseWaitCursor = true; Enabled = false; await action(); } catch (Exception ex) { MessageBox.Show(ex.Message, "Cel-Fii Ventas", MessageBoxButtons.OK, MessageBoxIcon.Error); } finally { Enabled = true; UseWaitCursor = false; } }

    private TabPage NewTab(string text) { var tab = new TabPage(text) { BackColor = Ink,
        ForeColor = Color.White, Padding = new Padding(0) }; _tabs.TabPages.Add(tab); return tab; }
    private static DataGridView Grid() => new() { Dock = DockStyle.Fill, ReadOnly = true, AllowUserToAddRows = false, AllowUserToDeleteRows = false, SelectionMode = DataGridViewSelectionMode.FullRowSelect, MultiSelect = false, BackgroundColor = Ink, GridColor = Color.FromArgb(45, 50, 45), ForeColor = Color.White, RowHeadersVisible = false, AutoSizeRowsMode = DataGridViewAutoSizeRowsMode.AllCells, BorderStyle = BorderStyle.None, DefaultCellStyle = new DataGridViewCellStyle { BackColor = PanelColor, ForeColor = Color.White, SelectionBackColor = Color.FromArgb(65, 90, 35), SelectionForeColor = Color.White, Padding = new Padding(6) }, ColumnHeadersDefaultCellStyle = new DataGridViewCellStyle { BackColor = Color.Black, ForeColor = Lime, Font = new Font("Segoe UI", 10, FontStyle.Bold) }, EnableHeadersVisualStyles = false };
    private static Button ActionButton(string text, EventHandler action) { var b = new Button { Text = text, AutoSize = true, Height = 46, BackColor = Lime, ForeColor = Color.Black, FlatStyle = FlatStyle.Flat, Font = new("Segoe UI", 10, FontStyle.Bold), Margin = new Padding(6) }; b.FlatAppearance.BorderSize = 0; b.Click += action; return b; }
    private static Label LabelFor(string text) => new() { Text = text, ForeColor = Color.White, Font = new("Segoe UI", 11, FontStyle.Bold), AutoSize = true, Padding = new Padding(0, 10, 0, 0) };
    private static void StyleInput(TextBox text) { text.BackColor = PanelColor; text.ForeColor = Color.White; text.BorderStyle = BorderStyle.FixedSingle; text.Font = new("Segoe UI", 13); }
}

internal sealed class ProductDialog : Form
{
    public Product Product { get; }
    public string[] PhotoPaths { get; } = new string[3];
    public ProductDialog(Product product, bool creating)
    {
        Product = product; Text = creating ? "Nuevo producto" : "Editar producto"; Width = 570; Height = 650; StartPosition = FormStartPosition.CenterParent; BackColor = Color.FromArgb(18, 22, 18); ForeColor = Color.White;
        var equipment = product.Type == "Equipo";
        var fields = new List<(string Label, Func<string> Get, Action<string> Set)> {
            (equipment ? "Modelo *" : "Nombre *", () => product.Name, v => product.Name = v),
            ("Precio efectivo/transferencia *", () => product.CashPrice.ToString(), v => product.CashPrice = Decimal(v)),
            ("Marca", () => product.Brand, v => product.Brand = v), ("Color", () => product.Color, v => product.Color = v),
            ("Costo", () => product.Cost.ToString(), v => product.Cost = Decimal(v))
        };
        if (equipment) fields.AddRange([
            ("Memoria", () => product.Memory, v => product.Memory = v), ("IMEI", () => product.Imei, v => product.Imei = v),
            ("Condición: Nuevo o Usado", () => product.Condition, v => product.Condition = v),
            ("Estado de batería", () => product.Battery, v => product.Battery = v),
            ("Observaciones", () => product.Observations, v => product.Observations = v),
            ("Estado: Disponible o Reservado", () => product.EquipmentStatus, v => product.EquipmentStatus = v),
            ("Cliente de reserva", () => product.ReservationCustomer, v => product.ReservationCustomer = v),
            ("Teléfono de reserva", () => product.ReservationPhone, v => product.ReservationPhone = v),
            ("Seña", () => product.ReservationDeposit.ToString(), v => product.ReservationDeposit = Decimal(v)),
            ("Vencimiento dd/MM/yyyy", () => product.ReservationExpiry, v => product.ReservationExpiry = v),
            ("Foto 1 (ruta Drive)", () => product.Photo, v => product.Photo = v), ("Foto 2 (ruta Drive)", () => product.Photo2, v => product.Photo2 = v),
            ("Foto 3 (ruta Drive)", () => product.Photo3, v => product.Photo3 = v)
        ]); else fields.AddRange([
            ("Categoría", () => product.Category, v => product.Category = v),
            ("Stock inicial / actual *", () => product.Stock.ToString(), v => product.Stock = Integer(v)),
            ("Modelos compatibles", () => product.CompatibleModels, v => product.CompatibleModels = v),
            ("Proveedor", () => product.Supplier, v => product.Supplier = v),
            ("Calidad", () => product.Quality, v => product.Quality = v),
            ("Garantía", () => product.WarrantyInfo, v => product.WarrantyInfo = v),
            ("Código de barras", () => product.Code, v => product.Code = v),
            ("Código alternativo", () => product.BackupCode, v => product.BackupCode = v),
            ("Foto (ruta Drive)", () => product.Photo, v => product.Photo = v)
        ]);
        var layout = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(18), RowCount = fields.Count * 2 + 1, AutoScroll = true };
        foreach (var f in fields) { layout.Controls.Add(new Label { Text = f.Label, AutoSize = true, ForeColor = Color.FromArgb(157,255,0), Padding = new Padding(0,6,0,2) }); var input = new TextBox { Text = f.Get(), Dock = DockStyle.Top, BackColor = Color.FromArgb(30,34,30), ForeColor = Color.White, Font = new("Segoe UI", 11) }; input.TextChanged += (_, _) => f.Set(input.Text); layout.Controls.Add(input); }
        var photoCount = equipment ? 3 : 1;
        for (var slot = 0; slot < photoCount; slot++) {
            var selectedSlot = slot;
            var photo = new Button { Text = $"ELEGIR FOTO {slot + 1}", Height = 42, Dock = DockStyle.Top,
                BackColor = Color.FromArgb(50,55,50), ForeColor = Color.White, FlatStyle = FlatStyle.Flat };
            photo.Click += (_, _) => { using var picker = new OpenFileDialog { Filter = "Imágenes|*.jpg;*.jpeg;*.png" }; if (picker.ShowDialog(this) == DialogResult.OK) { PhotoPaths[selectedSlot] = picker.FileName; photo.Text = $"FOTO {selectedSlot + 1}: {Path.GetFileName(picker.FileName)}"; } };
            layout.Controls.Add(photo);
        }
        var save = new Button { Text = "GUARDAR", DialogResult = DialogResult.OK, Height = 46, Dock = DockStyle.Top, BackColor = Color.FromArgb(157,255,0), FlatStyle = FlatStyle.Flat, Font = new("Segoe UI", 10, FontStyle.Bold) }; layout.Controls.Add(save); Controls.Add(layout); AcceptButton = save;
    }
    private static decimal Decimal(string value) => decimal.TryParse(value, out var n) ? n : 0;
    private static int Integer(string value) => int.TryParse(value, out var n) ? n : 0;
}
