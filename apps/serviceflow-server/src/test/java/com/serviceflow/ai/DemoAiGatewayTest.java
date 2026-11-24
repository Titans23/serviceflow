package com.serviceflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.serviceflow.agent.Intent;
import org.junit.jupiter.api.Test;

class DemoAiGatewayTest {
    private final DemoAiGateway gateway = new DemoAiGateway();

    @Test
    void decliningALookupWithoutAnotherRequestRemainsChat() {
        assertThat(gateway.classify("先不用查商品，我只是打个招呼。")).contains(Intent.CHAT);
        assertThat(gateway.classify("暂时没有具体的商品或订单问题。")).contains(Intent.CHAT);
        assertThat(gateway.classify("目前没有售后需求，谢谢。")).contains(Intent.CHAT);
    }

    @Test
    void preservesPositiveRequestsAndMissingDeliveryOrRefundComplaints() {
        assertThat(gateway.classify("不用查商品，但帮我查询订单。")).contains(Intent.ORDER_QUERY);
        assertThat(gateway.classify("不需要查询订单而是对比商品。")).contains(Intent.PRODUCT_QUERY);
        assertThat(gateway.classify("没有收到订单，请查物流。")).contains(Intent.ORDER_QUERY);
        assertThat(gateway.classify("暂时没有收到退款。")).contains(Intent.ORDER_QUERY);
        assertThat(gateway.classify("不用查商品，我要投诉。")).contains(Intent.COMPLAINT);
    }
}
