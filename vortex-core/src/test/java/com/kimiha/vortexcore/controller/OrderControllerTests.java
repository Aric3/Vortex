package com.kimiha.vortexcore.controller;

import com.kimiha.vortexcore.model.OrderEntity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderControllerTests {

    @LocalServerPort
    private int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    private OrderEntity validOrder(String clOrderId) {
        OrderEntity order = new OrderEntity();
        order.setClOrderId(clOrderId);
        order.setMarket("XSHG");
        order.setSecurityId("600030");
        order.setSide("B");
        order.setQty(100);
        order.setPrice(10.5);
        order.setShareholderId("SH12345678");
        return order;
    }

    @Test
    void createOrder_success() {
        OrderEntity order = validOrder("ORDER0000000001");

        WebTestClient client = client();

        client.post().uri("/api/v1/vclient/orders")
                .bodyValue(order)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.clOrderId").isEqualTo("ORDER0000000001");
    }

    @Test
    void createOrder_validationFailed() {
        OrderEntity order = validOrder("ORDER0000000002");
        order.setClOrderId(""); // invalid

        WebTestClient client = client();

        client.post().uri("/api/v1/vclient/orders")
                .bodyValue(order)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(1000);
    }

    @Test
    void createOrder_marketInvalidValue() {
        OrderEntity order = validOrder("ORDER0000000003");
        order.setMarket("ABCD");
        client().post().uri("/api/v1/vclient/orders")
                .bodyValue(order)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(1000);
    }

    @Test
    void createOrder_marketWrongLength() {
        OrderEntity order = validOrder("ORDER0000000004");
        order.setMarket("XSHEE"); // length 5
        client().post().uri("/api/v1/vclient/orders")
                .bodyValue(order)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(1000);
    }

    @Test
    void createOrder_sideInvalid() {
        OrderEntity order = validOrder("ORDER0000000005");
        order.setSide("X");
        client().post().uri("/api/v1/vclient/orders")
                .bodyValue(order)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(1000);
    }

    @Test
    void createOrder_securityIdInvalidFormat() {
        OrderEntity order = validOrder("ORDER0000000006");
        order.setSecurityId("60A030");
        client().post().uri("/api/v1/vclient/orders")
                .bodyValue(order)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(1000);
    }

    @Test
    void createOrder_qtyNegative() {
        OrderEntity order = validOrder("ORDER0000000007");
        order.setQty(-1);
        client().post().uri("/api/v1/vclient/orders")
                .bodyValue(order)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(1000);
    }

    @Test
    void createOrder_qtyZeroPass() {
        OrderEntity order = validOrder("ORDER0000000008");
        order.setQty(0);
        client().post().uri("/api/v1/vclient/orders")
                .bodyValue(order)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.code").isEqualTo(0);
    }

    @Test
    void createOrder_priceZeroFail() {
        OrderEntity order = validOrder("ORDER0000000009");
        order.setPrice(0.0);
        client().post().uri("/api/v1/vclient/orders")
                .bodyValue(order)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(1000);
    }

    @Test
    void createOrder_priceSmallPositivePass() {
        OrderEntity order = validOrder("ORDER0000000010");
        order.setPrice(0.01);
        client().post().uri("/api/v1/vclient/orders")
                .bodyValue(order)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.code").isEqualTo(0);
    }

    @Test
    void createOrder_clOrderIdLengthBoundaryPass() {
        OrderEntity order = validOrder("ABCDEFGHIJKLMNOP"); // length 16
        client().post().uri("/api/v1/vclient/orders")
                .bodyValue(order)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.code").isEqualTo(0);
    }

    @Test
    void createOrder_clOrderIdTooLongFail() {
        OrderEntity order = validOrder("ABCDEFGHIJKLMNOPQ"); // length 17
        client().post().uri("/api/v1/vclient/orders")
                .bodyValue(order)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(1000);
    }

    @Test
    void createOrder_shareholderIdBoundaryPass() {
        OrderEntity order = validOrder("ORDER0000000011");
        order.setShareholderId("SH1234567X"); // length 10
        client().post().uri("/api/v1/vclient/orders")
                .bodyValue(order)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.code").isEqualTo(0);
    }

    @Test
    void createOrder_shareholderIdTooLongFail() {
        OrderEntity order = validOrder("ORDER0000000012");
        order.setShareholderId("SH1234567XX"); // length 11
        client().post().uri("/api/v1/vclient/orders")
                .bodyValue(order)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(1000);
    }

    @Test
    void getAllOrders_returnsList() {
        // ensure at least one order exists
        client().post().uri("/api/v1/vclient/orders")
                .bodyValue(validOrder("ORDER_LIST_CHECK"))
                .exchange()
                .expectStatus().isOk();

        client().get().uri("/api/v1/vclient/orders")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data").isArray();
    }

    @Test
    void getOrderByClOrderId_notFound() {
        client().get().uri("/api/v1/vclient/orders/{clOrderId}", "NOTEXIST12345678")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo(1001);
    }
}
