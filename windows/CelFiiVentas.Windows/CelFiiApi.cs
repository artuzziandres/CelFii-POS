using System.Net.Http.Json;
using System.Text.Json;

namespace CelFiiVentas.Windows;

public sealed class CelFiiApi
{
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(45) };
    private readonly Func<AppSettings> _settings;
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };

    public CelFiiApi(Func<AppSettings> settings) => _settings = settings;

    public async Task TestAsync()
    {
        using var doc = await GetAsync("health");
        EnsureOk(doc.RootElement);
    }

    public async Task<List<Product>> GetProductsAsync(string query = "")
    {
        using var doc = await GetAsync("products", ("q", query));
        EnsureOk(doc.RootElement);
        return doc.RootElement.GetProperty("products").Deserialize<List<Product>>(JsonOptions) ?? [];
    }

    public async Task<List<Sale>> GetSalesAsync(string month = "")
    {
        using var doc = await GetAsync("sales", ("month", month));
        EnsureOk(doc.RootElement);
        return doc.RootElement.GetProperty("sales").Deserialize<List<Sale>>(JsonOptions) ?? [];
    }

    public async Task<string> CreateSaleAsync(IEnumerable<CartLine> lines, string seller, string payment)
    {
        var total = lines.Sum(x => x.Total);
        var result = await PostAsync(new
        {
            action = "createSale",
            clientRequestId = Guid.NewGuid().ToString("N"),
            seller,
            lines = lines.Select(x => new { productId = x.Product.Id, quantity = x.Quantity, unitPrice = x.UnitPrice }),
            payments = new[] { new { method = payment, amount = total } }
        });
        return result.TryGetProperty("saleId", out var id) ? id.GetString() ?? "" : "";
    }

    public async Task SaveProductAsync(Product product, bool creating)
    {
        await PostAsync(new { action = creating ? "createProduct" : "updateProduct", product });
    }

    private async Task<JsonDocument> GetAsync(string action, params (string Key, string Value)[] args)
    {
        var s = ValidateSettings();
        var query = new List<string> { "action=" + Uri.EscapeDataString(action), "token=" + Uri.EscapeDataString(s.Token) };
        query.AddRange(args.Select(x => Uri.EscapeDataString(x.Key) + "=" + Uri.EscapeDataString(x.Value)));
        var text = await _http.GetStringAsync(s.ApiUrl.TrimEnd('/') + "?" + string.Join("&", query));
        return JsonDocument.Parse(text);
    }

    private async Task<JsonElement> PostAsync(object body)
    {
        var s = ValidateSettings();
        var payload = JsonSerializer.SerializeToElement(body);
        var fields = payload.EnumerateObject().ToDictionary(x => x.Name, x => (object?)x.Value);
        fields["token"] = s.Token;
        using var response = await _http.PostAsJsonAsync(s.ApiUrl, fields);
        var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        EnsureOk(doc.RootElement);
        return doc.RootElement.Clone();
    }

    private AppSettings ValidateSettings()
    {
        var s = _settings();
        if (!Uri.TryCreate(s.ApiUrl, UriKind.Absolute, out _)) throw new InvalidOperationException("Configurá la URL del conector.");
        if (string.IsNullOrWhiteSpace(s.Token)) throw new InvalidOperationException("Configurá el token del conector en Más.");
        return s;
    }

    private static void EnsureOk(JsonElement root)
    {
        if (!root.TryGetProperty("ok", out var ok) || !ok.GetBoolean())
            throw new InvalidOperationException(root.TryGetProperty("error", out var e) ? e.GetString() : "Respuesta inválida del conector");
    }
}
