package com.volcanoartscenter.platform.shared.payment;

import com.volcanoartscenter.platform.shared.exception.BusinessRuleException;
import com.volcanoartscenter.platform.shared.exception.PlatformException;
import com.volcanoartscenter.platform.shared.model.Cart;
import com.volcanoartscenter.platform.shared.model.CartItem;
import com.volcanoartscenter.platform.shared.model.OrderItem;
import com.volcanoartscenter.platform.shared.model.Product;
import com.volcanoartscenter.platform.shared.model.ShippingOrder;
import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.repository.CartRepository;
import com.volcanoartscenter.platform.shared.repository.ProductRepository;
import com.volcanoartscenter.platform.shared.repository.ShippingOrderRepository;
import com.volcanoartscenter.platform.shared.service.ComplianceService;
import com.volcanoartscenter.platform.shared.service.integration.IntegrationFacadeService;
import com.volcanoartscenter.platform.shared.service.integration.impl.StripePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

    private static final String CURRENCY = "USD";

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final ShippingOrderRepository shippingOrderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentService paymentService;
    private final StripePaymentService stripePaymentService;
    private final IntegrationFacadeService integrationFacadeService;
    private final ComplianceService complianceService;

    public record CheckoutRequest(
            String recipientName,
            String recipientEmail,
            String recipientPhone,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            String country) {}

    public record CheckoutResult(
            String orderRef,
            Long paymentId,
            String gatewayRef,
            String clientSecret,
            String publishableKey,
            BigDecimal amount,
            String currency) {}

    @Transactional
    public CheckoutResult startCardCheckout(User user, CheckoutRequest request) {
        if (user == null) {
            throw new PlatformException("UNAUTHENTICATED", "Authentication required.", HttpStatus.UNAUTHORIZED);
        }
        if (!stripePaymentService.isConfigured()) {
            throw new PlatformException(
                    "STRIPE_NOT_CONFIGURED",
                    "Card payments are not currently available. Please try again later.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        validateAddress(request);

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new BusinessRuleException("CART_EMPTY", "Your cart is empty."));
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BusinessRuleException("CART_EMPTY", "Your cart is empty.");
        }

        String normalizedEmail = request.recipientEmail().trim().toLowerCase(Locale.ROOT);
        String country = request.country().trim();
        boolean isLocal = "Rwanda".equalsIgnoreCase(country);

        ShippingOrder order = ShippingOrder.builder()
                .orderReference("SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT))
                .user(user)
                .recipientName(request.recipientName().trim())
                .recipientEmail(normalizedEmail)
                .recipientPhone(request.recipientPhone())
                .addressLine1(request.addressLine1().trim())
                .addressLine2(request.addressLine2())
                .city(request.city().trim())
                .state(request.state())
                .postalCode(request.postalCode())
                .country(country)
                .carrier(isLocal ? ShippingOrder.ShippingCarrier.LOCAL : ShippingOrder.ShippingCarrier.FEDEX)
                .currency(CURRENCY)
                .paymentMethod("STRIPE_CARD")
                .paymentStatus(ShippingOrder.PaymentStatus.UNPAID)
                .status(ShippingOrder.OrderStatus.PENDING)
                .build();

        BigDecimal productTotal = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();

        for (CartItem ci : cart.getItems()) {
            Product fresh = productRepository.findById(ci.getProduct().getId())
                    .orElseThrow(() -> new BusinessRuleException("PRODUCT_UNAVAILABLE",
                            "An item in your cart is no longer available."));
            if (fresh.getArtworkStatus() != Product.ArtworkStatus.PUBLISHED
                    || !Boolean.TRUE.equals(fresh.getAvailable())) {
                throw new BusinessRuleException("PRODUCT_UNAVAILABLE",
                        "'" + fresh.getName() + "' is no longer available for purchase.");
            }
            int qty = Math.max(1, ci.getQuantity());
            if (fresh.getInventoryType() == Product.InventoryType.BATCH
                    && (fresh.getStockQuantity() == null || fresh.getStockQuantity() < qty)) {
                throw new BusinessRuleException("INSUFFICIENT_STOCK",
                        "'" + fresh.getName() + "' only has "
                                + (fresh.getStockQuantity() == null ? 0 : fresh.getStockQuantity())
                                + " in stock.");
            }
            BigDecimal unitPrice = fresh.getPrice();
            productTotal = productTotal.add(unitPrice.multiply(BigDecimal.valueOf(qty)));
            totalWeight = totalWeight.add(
                    (fresh.getWeightKg() == null ? new BigDecimal("1.0") : fresh.getWeightKg())
                            .multiply(BigDecimal.valueOf(qty)));
            items.add(OrderItem.builder()
                    .order(order)
                    .product(fresh)
                    .quantity(qty)
                    .priceAtPurchase(unitPrice)
                    .productName(fresh.getName())
                    .productImageUrl(fresh.getPrimaryImageUrl())
                    .build());
        }

        order.setOrderItems(items);
        order.setProductTotal(productTotal);
        if (!items.isEmpty()) {
            order.setProduct(items.get(0).getProduct());
            order.setQuantity(items.get(0).getQuantity());
        }

        BigDecimal shipping;
        try {
            shipping = integrationFacadeService.estimateShipping(
                    isLocal ? "LOCAL" : "FEDEX", country, totalWeight);
        } catch (RuntimeException ex) {
            log.warn("Shipping estimate failed for {}: {}", country, ex.getMessage());
            shipping = BigDecimal.ZERO;
        }
        order.setShippingCost(shipping);
        order.setTotalAmount(productTotal.add(shipping));

        ShippingOrder saved = shippingOrderRepository.save(order);

        StripePaymentService.CardIntent intent = stripePaymentService.initializeCardIntent(
                saved.getOrderReference(),
                saved.getTotalAmount(),
                saved.getCurrency(),
                Map.of(
                        "orderId", String.valueOf(saved.getId()),
                        "orderReference", saved.getOrderReference(),
                        "userId", String.valueOf(user.getId()),
                        "email", normalizedEmail));

        Payment payment = paymentService.createPending(
                PaymentGateway.STRIPE_CARD,
                intent.id(),
                PaymentSourceType.SHIPPING_ORDER,
                saved.getId(),
                saved.getTotalAmount(),
                saved.getCurrency(),
                user.getId(),
                normalizedEmail);

        saved.setPaymentTransactionId(intent.id());
        shippingOrderRepository.save(saved);

        orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(saved.getId())
                .previousStatus(null)
                .newStatus(saved.getStatus().name())
                .previousPaymentStatus(null)
                .newPaymentStatus(saved.getPaymentStatus().name())
                .actor(OrderStatusHistory.Actor.USER)
                .actorUserId(user.getId())
                .reason("Checkout initiated; awaiting card capture.")
                .build());

        cart.getItems().clear();
        cartRepository.save(cart);

        complianceService.recordConsent(normalizedEmail, "ORDER_TERMS", true, "api-checkout");
        complianceService.audit(normalizedEmail, "CHECKOUT_INITIATED", "ShippingOrder", saved.getId(),
                "ref=" + saved.getOrderReference()
                        + ", paymentId=" + payment.getId()
                        + ", intent=" + intent.id()
                        + ", total=" + saved.getTotalAmount());

        return new CheckoutResult(
                saved.getOrderReference(),
                payment.getId(),
                intent.id(),
                intent.clientSecret(),
                stripePaymentService.publishableKey(),
                saved.getTotalAmount(),
                saved.getCurrency());
    }

    private void validateAddress(CheckoutRequest r) {
        requireText(r.recipientName(), "recipientName", "Recipient name is required.");
        requireText(r.recipientEmail(), "recipientEmail", "Recipient email is required.");
        requireText(r.addressLine1(), "addressLine1", "Shipping address is required.");
        requireText(r.city(), "city", "City is required.");
        requireText(r.country(), "country", "Country is required.");
    }

    private void requireText(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException("VALIDATION_FAILED", message,
                    Map.of("field", field));
        }
    }
}
