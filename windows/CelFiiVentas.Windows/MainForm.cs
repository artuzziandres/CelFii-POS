using System.Drawing.Printing;

namespace CelFiiVentas.Windows;

public sealed class MainForm : Form
{
    private static readonly Color Lime = Color.FromArgb(157, 255, 0);
    private static readonly Color Ink = Color.FromArgb(8, 10, 9);
    private static readonly Color PanelColor = Color.FromArgb(23, 27, 24);
    private static readonly Color SoftPanel = Color.FromArgb(34, 40, 35);
    private readonly AppSettings _settings = SettingsStore.Load();
    private readonly CelFiiApi _api;
    private readonly TabControl _tabs = new() { Dock = DockStyle.Fill, Appearance = TabAppearance.FlatButtons,
        SizeMode = TabSizeMode.Fixed, ItemSize = new Size(0, 1), Padding = Point.Empty };
    private readonly List<Button> _navButtons = [];
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
    private readonly System.Windows.Forms.Timer _searchDelay = new() { Interval = 220 };
    private bool _historyLoaded;
    private List<Product> _allProducts = [];
    private readonly List<CartLine> _cartLines = [];

    public MainForm()
    {
        _api = new(() => _settings);
        Text = "Cel-Fii Ventas";
        AutoScaleMode = AutoScaleMode.None;
        MinimumSize = new Size(1100, 720);
        WindowState = FormWindowState.Maximized;
        BackColor = Ink;
        ForeColor = Color.White;
        Font = new Font("Segoe UI", 10);
        BuildSaleTab();
        BuildProductsTab();
        BuildHistoryTab();
        BuildMoreTab();
        Controls.Add(_tabs);
        Controls.Add(BuildNavigation());
        Controls.Add(BuildHeader());
        _searchDelay.Tick += (_, _) => { _searchDelay.Stop(); FilterProducts(); };
        _tabs.SelectedIndexChanged += async (_, _) => {
            UpdateNavigation();
            if (_tabs.SelectedTab?.Text == "HISTORIAL" && !_historyLoaded) await ReloadHistoryAsync();
        };
        Shown += async (_, _) => await ReloadProductsAsync();
    }

    private Control BuildNavigation()
    {
        var nav = new FlowLayoutPanel { Dock = DockStyle.Top, Height = 64, BackColor = Color.FromArgb(17, 21, 18),
            Padding = new Padding(28, 10, 28, 10), WrapContents = false };
        var labels = new[] { "VENTA", "PRODUCTOS", "HISTORIAL", "CONFIGURACIÓN" };
        for (var i = 0; i < labels.Length; i++) {
            var index = i;
            var button = new Button { Text = labels[i], Width = i == 3 ? 200 : 160, Height = 44,
                FlatStyle = FlatStyle.Flat, Font = new Font("Segoe UI", 10, FontStyle.Bold),
                Cursor = Cursors.Hand, Margin = new Padding(0, 0, 8, 0) };
            button.FlatAppearance.BorderSize = 0; button.Click += (_, _) => _tabs.SelectedIndex = index;
            _navButtons.Add(button); nav.Controls.Add(button);
        }
        UpdateNavigation(); return nav;
    }

    private void UpdateNavigation()
    {
        for (var i = 0; i < _navButtons.Count; i++) {
            var active = i == _tabs.SelectedIndex;
            _navButtons[i].BackColor = active ? Lime : Color.FromArgb(31, 37, 32);
            _navButtons[i].ForeColor = active ? Color.Black : Color.White;
        }
    }

    private Control BuildHeader()
    {
        var header = new Panel { Dock = DockStyle.Top, Height = 112, BackColor = Color.FromArgb(12, 14, 16) };
        var logoPath = Path.Combine(AppContext.BaseDirectory, "logo_celfii_app.png");
        if (File.Exists(logoPath)) header.Controls.Add(new PictureBox { Image = Image.FromFile(logoPath), SizeMode = PictureBoxSizeMode.Zoom, Bounds = new Rectangle(24, 14, 82, 82) });
        header.Controls.Add(new Label { Text = "CEL-FII", ForeColor = Lime, Font = new("Segoe UI", 26, FontStyle.Bold), AutoSize = true, Location = new Point(122, 18) });
        header.Controls.Add(new Label { Text = "VENTAS PARA WINDOWS", ForeColor = Color.FromArgb(185, 190, 195), Font = new("Segoe UI", 10, FontStyle.Bold), AutoSize = true, Location = new Point(126, 70) });
        _status.AutoSize = false; _status.Size = new Size(420, 40); _status.TextAlign = ContentAlignment.MiddleRight;
        _status.Font = new Font("Segoe UI", 10); _status.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        header.Controls.Add(_status); header.Resize += (_, _) => _status.Location = new Point(header.ClientSize.Width - 448, 34);
        return header;
    }

    private void BuildSaleTab()
    {
        var tab = NewTab("VENTA");
        var split = new SplitContainer { Dock = DockStyle.Fill, SplitterWidth = 8,
            FixedPanel = FixedPanel.Panel2,
            BackColor = Color.FromArgb(40, 45, 40) };
        split.Resize += (_, _) => { if (split.ClientSize.Width > 1050)
            split.SplitterDistance = Math.Max(600, split.ClientSize.Width - 450); };
        tab.Controls.Add(split);
        var left = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 4,
            Padding = new Padding(24, 20, 20, 20), BackColor = Ink };
        left.RowStyles.Add(new RowStyle(SizeType.Absolute, 48));
        left.RowStyles.Add(new RowStyle(SizeType.Absolute, 36));
        left.RowStyles.Add(new RowStyle(SizeType.Absolute, 62));
        left.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        _saleType.Items.AddRange(["Todos", "Accesorios", "Repuestos", "Equipos"]); _saleType.SelectedIndex = 0;
        _saleType.Dock = DockStyle.Fill; _saleType.Font = new Font("Segoe UI", 11, FontStyle.Bold);
        _saleType.BackColor = Color.FromArgb(31, 38, 31); _saleType.ForeColor = Color.White;
        _saleType.SelectedIndexChanged += (_, _) => FilterProducts();
        _search.Dock = DockStyle.Fill; StyleInput(_search); _search.TextChanged += (_, _) => {
            _searchDelay.Stop(); _searchDelay.Start();
        };
        _search.KeyDown += (_, e) => { if (e.KeyCode == Keys.Enter) { AddExactCode(); e.SuppressKeyPress = true; } };
        left.Controls.Add(_saleType, 0, 0);
        left.Controls.Add(new Label { Text = "DOBLE CLIC EN UN PRODUCTO PARA AGREGARLO AL TICKET",
            ForeColor = Lime, Font = new("Segoe UI", 10, FontStyle.Bold), AutoSize = true,
            Padding = new Padding(0, 8, 0, 0) }, 0, 1);
        left.Controls.Add(_search, 0, 2); left.Controls.Add(_products, 0, 3);
        split.Panel1.Controls.Add(left);
        _products.CellDoubleClick += (_, e) => { if (e.RowIndex >= 0) AddProduct((Product)_products.Rows[e.RowIndex].Tag); };
        _cart.CellDoubleClick += (_, e) => { if (e.RowIndex >= 0) ChangePrice(e.RowIndex); };

        var right = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 8,
            Padding = new Padding(22, 18, 22, 18), BackColor = PanelColor };
        right.RowStyles.Add(new RowStyle(SizeType.Absolute, 52)); right.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        right.RowStyles.Add(new RowStyle(SizeType.Absolute, 38)); right.RowStyles.Add(new RowStyle(SizeType.Absolute, 52));
        right.RowStyles.Add(new RowStyle(SizeType.Absolute, 38)); right.RowStyles.Add(new RowStyle(SizeType.Absolute, 52));
        right.RowStyles.Add(new RowStyle(SizeType.Absolute, 62)); right.RowStyles.Add(new RowStyle(SizeType.Absolute, 126));
        right.Controls.Add(new Label { Text = "TICKET", ForeColor = Lime, Font = new("Segoe UI", 18, FontStyle.Bold), AutoSize = true }, 0, 0);
        right.Controls.Add(_cart, 0, 1); right.Controls.Add(LabelFor("Vendedor"), 0, 2); right.Controls.Add(_seller, 0, 3);
        right.Controls.Add(LabelFor("Forma de pago"), 0, 4); right.Controls.Add(_payment, 0, 5);
        right.Controls.Add(_total, 0, 6); _total.Dock = DockStyle.Fill; _total.TextAlign = ContentAlignment.MiddleLeft;
        var actions = new TableLayoutPanel { Dock = DockStyle.Fill, RowCount = 2, ColumnCount = 1,
            Padding = new Padding(0, 8, 0, 0) };
        actions.RowStyles.Add(new RowStyle(SizeType.Percent, 50)); actions.RowStyles.Add(new RowStyle(SizeType.Percent, 50));
        var print = ActionButton("GUARDAR E IMPRIMIR", async (_, _) => await FinishSaleAsync(true));
        var save = ActionButton("SOLO GUARDAR", async (_, _) => await FinishSaleAsync(false));
        save.Dock = DockStyle.Fill; print.Dock = DockStyle.Fill;
        actions.Controls.Add(save, 0, 0); actions.Controls.Add(print, 0, 1);
        right.Controls.Add(actions, 0, 7);
        split.Panel2.Controls.Add(right);
        _seller.Items.AddRange(["Andres", "Maxi", "Gaby", "Facu", "Malena", "Benjamin", "Alejandra", "Elio"]); _seller.SelectedIndex = 0;
        _payment.Items.AddRange(["Efectivo", "Transferencia", "Posnet"]); _payment.SelectedIndex = 0;
    }

    private void BuildProductsTab()
    {
        var tab = NewTab("PRODUCTOS");
        var panel = new Panel { Dock = DockStyle.Fill, Padding = new Padding(24, 18, 24, 22), BackColor = Ink };
        var bar = new FlowLayoutPanel { Dock = DockStyle.Top, Height = 76, Padding = new Padding(0, 10, 0, 10), WrapContents = false };
        _productType.Items.AddRange(["Todos", "Accesorios", "Repuestos", "Equipos"]); _productType.SelectedIndex = 0;
        _productType.Width = 190; _productType.Height = 50; _productType.Font = new Font("Segoe UI", 11);
        _productType.SelectedIndexChanged += (_, _) => FillProductAdmin();
        bar.Controls.Add(_productType);
        bar.Controls.Add(ActionButton("+ NUEVO PRODUCTO", (_, _) => ChooseProductType()));
        bar.Controls.Add(ActionButton("EDITAR PRODUCTO", (_, _) => { if (_productAdmin.CurrentRow?.Tag is Product p) EditProduct(p); }));
        bar.Controls.Add(ActionButton("ACTUALIZAR", async (_, _) => await ReloadProductsAsync()));
        bar.Controls.Add(new Label { Text = "Doble clic para editar", ForeColor = Color.Silver,
            AutoSize = true, Padding = new Padding(14, 14, 0, 0), Font = new("Segoe UI", 10, FontStyle.Italic) });
        panel.Controls.Add(_productAdmin); panel.Controls.Add(bar); tab.Controls.Add(panel);
        _productAdmin.CellDoubleClick += (_, e) => { if (e.RowIndex >= 0) EditProduct((Product)_productAdmin.Rows[e.RowIndex].Tag); };
    }

    private void BuildHistoryTab()
    {
        var tab = NewTab("HISTORIAL");
        var panel = new TableLayoutPanel { Dock = DockStyle.Fill, Padding = new Padding(24, 20, 24, 24), BackColor = Ink, RowCount = 3 };
        panel.RowStyles.Add(new RowStyle(SizeType.Absolute, 58)); panel.RowStyles.Add(new RowStyle(SizeType.Absolute, 64)); panel.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        panel.Controls.Add(new Label { Text = "HISTORIAL DE VENTAS", ForeColor = Color.White, Font = new("Segoe UI", 22, FontStyle.Bold), AutoSize = true }, 0, 0);
        var refresh = ActionButton("ACTUALIZAR HISTORIAL", async (_, _) => await ReloadHistoryAsync()); refresh.Dock = DockStyle.Left;
        panel.Controls.Add(refresh, 0, 1); panel.Controls.Add(_history, 0, 2); tab.Controls.Add(panel);
        _history.ItemHeight = 38; _history.ShowLines = false; _history.FullRowSelect = true; _history.ShowRootLines = false;
        _history.NodeMouseDoubleClick += (_, e) => { if (e.Node.Tag is Sale s) ShowSale(s); };
    }

    private void BuildMoreTab()
    {
        var tab = NewTab("MÁS");
        var page = new Panel { Dock = DockStyle.Fill, BackColor = Ink, AutoScroll = true };
        var card = new Panel { BackColor = PanelColor, Size = new Size(900, 620) };
        page.Controls.Add(card); tab.Controls.Add(page);
        var title = new Label { Text = "Configuración", ForeColor = Color.White, Font = new("Segoe UI", 24, FontStyle.Bold), AutoSize = true, Location = new Point(40, 34) };
        var subtitle = new Label { Text = "Conexión con Google Sheets e impresión del mostrador", ForeColor = Color.FromArgb(180,185,190), Font = new("Segoe UI", 11), AutoSize = true, Location = new Point(42, 84) };
        var urlLabel = FieldLabel("URL del conector", 142);
        var url = new TextBox { Text = _settings.ApiUrl, Location = new Point(42, 176), Size = new Size(816, 42), Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right }; StyleInput(url);
        var tokenLabel = FieldLabel("Token privado", 238);
        var token = new TextBox { Text = _settings.Token, UseSystemPasswordChar = true, Location = new Point(42, 272), Size = new Size(816, 42), Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right }; StyleInput(token);
        var printerLabel = FieldLabel("Impresora de tickets", 334);
        var printers = new ComboBox { Location = new Point(42, 368), Size = new Size(816, 42), DropDownStyle = ComboBoxStyle.DropDownList,
            Font = new Font("Segoe UI", 11), BackColor = Color.White, ForeColor = Color.Black, Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right };
        foreach (string name in PrinterSettings.InstalledPrinters) printers.Items.Add(name);
        if (printers.Items.Contains(_settings.PrinterName)) printers.SelectedItem = _settings.PrinterName; else if (printers.Items.Count > 0) printers.SelectedIndex = 0;
        var hint = new Label { Text = "Enter: guardar y comprobar conexión", ForeColor = Color.FromArgb(160,165,170), Font = new("Segoe UI", 9.5f), AutoSize = true, Location = new Point(42, 426) };
        void SaveSettings() { _settings.ApiUrl = url.Text.Trim(); _settings.Token = token.Text.Trim(); _settings.PrinterName = printers.Text; SettingsStore.Save(_settings); }
        var save = ActionButton("GUARDAR", (_, _) => { SaveSettings(); _status.Text = "● CONFIGURACIÓN GUARDADA"; MessageBox.Show("Configuración guardada.", "Cel-Fii Ventas"); });
        var test = ActionButton("GUARDAR Y PROBAR CONEXIÓN", async (_, _) => { SaveSettings(); await RunBusy(async () => { await _api.TestAsync(); await ReloadProductsAsync(); _status.Text = $"● {_allProducts.Count} PRODUCTOS"; MessageBox.Show("Conexión correcta y productos sincronizados.", "Cel-Fii Ventas"); }); });
        save.Location = new Point(42, 476); save.Size = new Size(250, 54);
        test.Location = new Point(310, 476); test.Size = new Size(548, 54);
        token.KeyDown += (_, e) => { if (e.KeyCode == Keys.Enter) { test.PerformClick(); e.SuppressKeyPress = true; } };
        card.Controls.AddRange([title, subtitle, urlLabel, url, tokenLabel, token, printerLabel, printers, hint, save, test]);
        page.Resize += (_, _) => { card.Width = Math.Min(900, Math.Max(720, page.ClientSize.Width - 80)); card.Left = Math.Max(40, (page.ClientSize.Width - card.Width) / 2); card.Top = 38; url.Width = token.Width = printers.Width = card.ClientSize.Width - 84; test.Width = card.ClientSize.Width - 352; };
    }

    private static Label FieldLabel(string text, int top) => new() { Text = text, ForeColor = Color.White,
        Font = new Font("Segoe UI", 10.5f, FontStyle.Bold), AutoSize = true, Location = new Point(42, top) };

    private async Task ReloadAllAsync() { await ReloadProductsAsync(); await ReloadHistoryAsync(); }
    private async Task ReloadProductsAsync() => await RunBusy(async () => { _allProducts = await _api.GetProductsAsync(); FilterProducts(); FillProductAdmin(); _status.Text = $"● {_allProducts.Count} PRODUCTOS"; });
    private async Task ReloadHistoryAsync() => await RunBusy(async () => { var sales = await _api.GetSalesAsync(); _history.Nodes.Clear(); foreach (var group in sales.GroupBy(SaleMonth).OrderByDescending(g => g.Key)) { var month = _history.Nodes.Add(group.Key); month.ForeColor = Lime; month.NodeFont = new Font("Segoe UI", 11, FontStyle.Bold); foreach (var s in group) { var node = month.Nodes.Add($"{s.Date}     {s.Seller}     {s.Total:C0}     {s.Payment}"); node.Tag = s; } } if (_history.Nodes.Count > 0) _history.Nodes[0].Expand(); _historyLoaded = true; });
    private static string SaleMonth(Sale sale) {
        if (DateTime.TryParse(sale.Date, out var date) || DateTime.TryParse(sale.Month, out date))
            return date.ToString("yyyy-MM | MMMM", new System.Globalization.CultureInfo("es-AR")).ToUpperInvariant();
        return string.IsNullOrWhiteSpace(sale.Month) ? "SIN FECHA" : sale.Month;
    }

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
    private static DataGridView Grid() => new SmoothGrid { Dock = DockStyle.Fill, ReadOnly = true,
        AllowUserToAddRows = false, AllowUserToDeleteRows = false,
        SelectionMode = DataGridViewSelectionMode.FullRowSelect, MultiSelect = false,
        BackgroundColor = Ink, GridColor = Color.FromArgb(48, 58, 48), ForeColor = Color.White,
        RowHeadersVisible = false, RowTemplate = { Height = 43 }, BorderStyle = BorderStyle.None,
        DefaultCellStyle = new DataGridViewCellStyle { BackColor = PanelColor, ForeColor = Color.White,
            SelectionBackColor = Color.FromArgb(72, 105, 34), SelectionForeColor = Color.White,
            Padding = new Padding(9, 5, 9, 5), Font = new Font("Segoe UI", 10.5f) },
        AlternatingRowsDefaultCellStyle = new DataGridViewCellStyle {
            BackColor = Color.FromArgb(16, 21, 17), ForeColor = Color.White },
        ColumnHeadersHeight = 44, ColumnHeadersHeightSizeMode = DataGridViewColumnHeadersHeightSizeMode.DisableResizing,
        ColumnHeadersDefaultCellStyle = new DataGridViewCellStyle { BackColor = Color.FromArgb(30, 45, 22),
            ForeColor = Lime, Font = new Font("Segoe UI", 10.5f, FontStyle.Bold),
            Padding = new Padding(8) }, EnableHeadersVisualStyles = false };
    private static Button ActionButton(string text, EventHandler action) { var b = new Button {
        Text = text, AutoSize = false, Size = new Size(190, 48), MinimumSize = new Size(150, 48), Height = 48,
        BackColor = Lime, ForeColor = Color.Black, FlatStyle = FlatStyle.Flat,
        Cursor = Cursors.Hand, Font = new("Segoe UI", 10, FontStyle.Bold), Margin = new Padding(6), Padding = new Padding(10, 0, 10, 0) };
        b.FlatAppearance.BorderSize = 0; b.FlatAppearance.MouseOverBackColor = Color.FromArgb(190, 255, 65);
        b.FlatAppearance.MouseDownBackColor = Color.FromArgb(125, 210, 0); b.Click += action; return b; }
    private static Label LabelFor(string text) => new() { Text = text, ForeColor = Color.White, Font = new("Segoe UI", 11, FontStyle.Bold), AutoSize = true, Padding = new Padding(0, 10, 0, 0) };
    private static void StyleInput(TextBox text) { text.BackColor = PanelColor; text.ForeColor = Color.White; text.BorderStyle = BorderStyle.FixedSingle; text.Font = new("Segoe UI", 13); }
}

internal sealed class SmoothGrid : DataGridView
{
    public SmoothGrid() { DoubleBuffered = true; SetStyle(ControlStyles.OptimizedDoubleBuffer, true); }
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
