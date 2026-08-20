using System.Text.Json.Serialization;

namespace CelFiiVentas.Windows;

public sealed class Product
{
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public string Category { get; set; } = "";
    public decimal CashPrice { get; set; }
    public decimal CardPrice { get; set; }
    public int Stock { get; set; }
    public string Photo { get; set; } = "";
    public string Photo2 { get; set; } = "";
    public string Photo3 { get; set; } = "";
    public string Code { get; set; } = "";
    public string BackupCode { get; set; } = "";
    public string Type { get; set; } = "";
    public string Description { get; set; } = "";
    public int MinimumStock { get; set; }
    public decimal CostUsd { get; set; }
    public decimal Cost { get; set; }
    public string Brand { get; set; } = "";
    public string CompatibleModels { get; set; } = "";
    public string Color { get; set; } = "";
    public string Supplier { get; set; } = "";
    public string Quality { get; set; } = "";
    public string WarrantyInfo { get; set; } = "";
    public string Imei { get; set; } = "";
    public string Memory { get; set; } = "";
    public string Condition { get; set; } = "Nuevo";
    public string Battery { get; set; } = "";
    public string Observations { get; set; } = "";
    public string EquipmentStatus { get; set; } = "Disponible";
    public string ReservationCustomer { get; set; } = "";
    public string ReservationPhone { get; set; } = "";
    public decimal ReservationDeposit { get; set; }
    public string ReservationDate { get; set; } = "";
    public string ReservationExpiry { get; set; } = "";
    [JsonIgnore] public bool IsEquipment => Type.Equals("Equipo", StringComparison.OrdinalIgnoreCase);
    [JsonIgnore] public bool IsSold => IsEquipment && EquipmentStatus.Equals("Vendido", StringComparison.OrdinalIgnoreCase);
}

public sealed class CartLine
{
    [JsonIgnore] public Product Product { get; init; } = new();
    public int Quantity { get; set; } = 1;
    public decimal UnitPrice { get; set; }
    public decimal OriginalPrice { get; set; }
    public decimal Difference { get; set; }
    public decimal Total => Quantity * UnitPrice;
}

public sealed class Sale
{
    public string Id { get; set; } = "";
    public long Timestamp { get; set; }
    public string Date { get; set; } = "";
    public string Month { get; set; } = "";
    public string Seller { get; set; } = "";
    public decimal Total { get; set; }
    public string Payment { get; set; } = "";
    public string Status { get; set; } = "";
    public List<SaleDetail> Details { get; set; } = [];
}

public sealed class SaleDetail
{
    public string ProductId { get; set; } = "";
    public string Name { get; set; } = "";
    public int Quantity { get; set; }
    public decimal UnitPrice { get; set; }
    public decimal Total { get; set; }
}

public sealed class AppSettings
{
    public string ApiUrl { get; set; } = "https://script.google.com/macros/s/AKfycbxfd_84OPqT-tTF_ZhO6zBYjGGiGDLsk_XoTffD1NMugaXbhltSUZ-YfncredvgSCBI/exec";
    public string Token { get; set; } = "";
    public string PrinterName { get; set; } = "";
}
