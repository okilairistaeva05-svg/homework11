package com.example.ecommerce;

import java.time.LocalDateTime;
import java.util.*;

public abstract class Account {
    protected UUID id;
    protected String name;
    protected String email;
    protected String phone;
    protected Address address;
    protected Role role;

    public Account(String name, String email, String phone, Address address, Role role) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.role = role;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }

    public abstract void register();
    public abstract boolean login(String email, String password);
    public void updateDetails(String name, String phone, Address address) {
        this.name = name;
        this.phone = phone;
        this.address = address;
    }
}

enum Role { CLIENT, ADMIN }

class Address {
    public String street;
    public String city;
    public String postalCode;
    public String country;

    public Address(String street, String city, String postalCode, String country) {
        this.street = street; this.city = city; this.postalCode = postalCode; this.country = country;
    }
}

class Client extends Account {
    private LoyaltyAccount loyaltyAccount;
    private List<Order> orders = new ArrayList<>();

    public Client(String name, String email, String phone, Address address) {
        super(name, email, phone, address, Role.CLIENT);
        this.loyaltyAccount = new LoyaltyAccount(this.id);
    }

    @Override
    public void register() {
        System.out.println("Client registered: " + email);
    }

    @Override
    public boolean login(String email, String password) {
        return this.email.equals(email);
    }

    public void addOrder(Order order) { orders.add(order); }
    public List<Order> getOrders() { return Collections.unmodifiableList(orders); }
    public LoyaltyAccount getLoyaltyAccount() { return loyaltyAccount; }
}

class Administrator extends Account {
    public Administrator(String name, String email, String phone, Address address) {
        super(name, email, phone, address, Role.ADMIN);
    }

    @Override
    public void register() {
        System.out.println("Admin registered: " + email);
    }

    @Override
    public boolean login(String email, String password) {
        return this.email.equals(email);
    }

    public void performAdminAction(String action, AdminActionLogger logger) {
        logger.log(this, action);
    }
}

class Category {
    public UUID id;
    public String name;

    public Category(String name) { this.id = UUID.randomUUID(); this.name = name; }
}

class Product {
    public UUID id;
    public String name;
    public String description;
    public double price;
    public Category category;

    public Product(String name, String description, double price, Category category) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
    }

    public void update(String name, String description, double price, Category category) {
        this.name = name; this.description = description; this.price = price; this.category = category;
    }
}

class Warehouse {
    public UUID id;
    public String name;
    public Address address;
    private Map<UUID, Integer> stock = new HashMap<>();

    public Warehouse(String name, Address address) {
        this.id = UUID.randomUUID(); this.name = name; this.address = address;
    }

    public void setStock(Product product, int qty) { stock.put(product.id, qty); }
    public int getStock(Product product) { return stock.getOrDefault(product.id, 0); }
    public boolean reserve(Product product, int qty) {
        int available = getStock(product);
        if (available >= qty) { stock.put(product.id, available - qty); return true; }
        return false;
    }
    public void release(Product product, int qty) { stock.put(product.id, getStock(product) + qty); }
}

enum OrderStatus { CREATED, PROCESSING, IN_DELIVERY, DELIVERED, CANCELLED }

class Order {
    public UUID id;
    public LocalDateTime createdAt;
    public OrderStatus status;
    public UUID clientId;
    public List<OrderItem> items = new ArrayList<>();
    public double totalAmount;
    public UUID shipmentId;

    public Order(UUID clientId) {
        this.id = UUID.randomUUID(); this.createdAt = LocalDateTime.now(); this.status = OrderStatus.CREATED; this.clientId = clientId;
    }

    public void addItem(Product product, int quantity) {
        items.add(new OrderItem(this, product, quantity, product.price));
        recalcTotal();
    }

    public void recalcTotal() {
        totalAmount = items.stream().mapToDouble(i -> i.unitPrice * i.quantity).sum();
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    public void pay(Payment payment, PaymentGateway gateway) throws PaymentException {
        boolean success = gateway.process(payment);
        if (success) {
            payment.status = PaymentStatus.COMPLETED;
            this.status = OrderStatus.PROCESSING;
        } else {
            payment.status = PaymentStatus.FAILED;
            throw new PaymentException("Payment failed");
        }
    }
}

class OrderItem {
    public UUID id;
    public UUID orderId;
    public UUID productId;
    public int quantity;
    public double unitPrice;

    public OrderItem(Order order, Product product, int quantity, double unitPrice) {
        this.id = UUID.randomUUID(); this.orderId = order.id; this.productId = product.id; this.quantity = quantity; this.unitPrice = unitPrice;
    }
}

class Cart {
    private UUID clientId;
    private Map<UUID, Integer> productQuantities = new HashMap<>();
    private PromoCode appliedPromo = null;

    public Cart(UUID clientId) { this.clientId = clientId; }

    public void add(Product p, int q) { productQuantities.put(p.id, productQuantities.getOrDefault(p.id,0)+q); }
    public void remove(Product p) { productQuantities.remove(p.id); }
    public void applyPromo(PromoCode promo) { this.appliedPromo = promo; }
}

class PromoCode {
    public String code;
    public double percentOff;
    public LocalDateTime expiresAt;

    public PromoCode(String code, double percentOff, LocalDateTime expiresAt) { this.code = code; this.percentOff = percentOff; this.expiresAt = expiresAt; }

    public boolean isValid() { return LocalDateTime.now().isBefore(expiresAt); }
}

enum PaymentType { CARD, E_WALLET }
enum PaymentStatus { PENDING, COMPLETED, FAILED, REFUNDED }

class Payment {
    public UUID id;
    public PaymentType type;
    public double amount;
    public PaymentStatus status;
    public LocalDateTime date;

    public Payment(PaymentType type, double amount) {
        this.id = UUID.randomUUID(); this.type = type; this.amount = amount; this.status = PaymentStatus.PENDING; this.date = LocalDateTime.now();
    }

    public void refund() { this.status = PaymentStatus.REFUNDED; }
}

interface PaymentGateway {
    boolean process(Payment payment);
}

class StripeGateway implements PaymentGateway {
    @Override
    public boolean process(Payment payment) {
        System.out.println("Processing payment via Stripe: " + payment.amount);
        return true;
    }
}

class PaymentException extends Exception { public PaymentException(String msg){ super(msg);} }

enum ShipmentStatus { READY, SHIPPED, IN_TRANSIT, DELIVERED }

class Shipment {
    public UUID id;
    public Address toAddress;
    public ShipmentStatus status;
    public UUID courierId;
    public UUID orderId;

    public Shipment(Address toAddress, UUID orderId) {
        this.id = UUID.randomUUID(); this.toAddress = toAddress; this.status = ShipmentStatus.READY; this.orderId = orderId;
    }

    public void dispatch(CourierAPI courierAPI) {
        courierAPI.createShipment(this);
        this.status = ShipmentStatus.SHIPPED;
    }

    public void track(CourierAPI courierAPI) { courierAPI.trackShipment(this.id); }
}

interface CourierAPI {
    void createShipment(Shipment shipment);
    void trackShipment(UUID shipmentId);
}

class DummyCourier implements CourierAPI {
    @Override
    public void createShipment(Shipment shipment) { System.out.println("Created shipment: " + shipment.id); }
    @Override
    public void trackShipment(UUID shipmentId) { System.out.println("Tracking shipment: " + shipmentId); }
}

class Review {
    public UUID id;
    public UUID productId;
    public UUID clientId;
    public int rating;
    public String comment;
    public LocalDateTime createdAt;

    public Review(UUID productId, UUID clientId, int rating, String comment) {
        this.id = UUID.randomUUID(); this.productId = productId; this.clientId = clientId; this.rating = rating; this.comment = comment; this.createdAt = LocalDateTime.now();
    }
}

class LoyaltyAccount {
    private UUID clientId;
    private int points;

    public LoyaltyAccount(UUID clientId) { this.clientId = clientId; this.points = 0; }
    public void addPoints(int p) { points += p; }
    public boolean usePoints(int p) { if (points>=p){ points -= p; return true;} return false; }
}

interface AdminActionLogger { void log(Administrator admin, String action); }
class SimpleAdminLogger implements AdminActionLogger {
    public void log(Administrator admin, String action) {
        System.out.println(LocalDateTime.now() + " - ADMIN[" + admin.getEmail() + "] : " + action);
    }
}

class ProductFactory {
    public static Product createProduct(String type, String name, String desc, double price, Category category) {
        switch (type.toLowerCase()) {
            case "digital":
                return new DigitalProduct(name, desc, price, category);
            case "physical":
            default:
                return new Product(name, desc, price, category);
        }
    }
}

class DigitalProduct extends Product {
    public String downloadUrl;
    public DigitalProduct(String name, String description, double price, Category category) {
        super(name, description, price, category);
    }
}
