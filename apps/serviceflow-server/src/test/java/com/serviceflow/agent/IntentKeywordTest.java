package com.serviceflow.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IntentKeywordTest {
    @Test
    void decliningALookupWithoutAnotherRequestRemainsChat() {
        assertThat(Intent.fromKeywords("先不用查商品，我只是打个招呼。")).isEqualTo(Intent.CHAT);
        assertThat(Intent.fromKeywords("暂时没有具体的商品或订单问题。")).isEqualTo(Intent.CHAT);
        assertThat(Intent.fromKeywords("目前没有售后需求，谢谢。")).isEqualTo(Intent.CHAT);
    }

    @Test
    void preservesPositiveRequestsAndMissingDeliveryOrRefundComplaints() {
        assertThat(Intent.fromKeywords("不用查商品，但帮我查询订单。")).isEqualTo(Intent.ORDER_QUERY);
        assertThat(Intent.fromKeywords("不需要查询订单而是对比商品。")).isEqualTo(Intent.PRODUCT_QUERY);
        assertThat(Intent.fromKeywords("没有收到订单，请查物流。")).isEqualTo(Intent.ORDER_QUERY);
        assertThat(Intent.fromKeywords("暂时没有收到退款。")).isEqualTo(Intent.ORDER_QUERY);
        assertThat(Intent.fromKeywords("不用查商品，我要投诉。")).isEqualTo(Intent.COMPLAINT);
    }
}
