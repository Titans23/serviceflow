package com.serviceflow.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.serviceflow.mapper.TicketMapper;
import com.serviceflow.model.TicketModels;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TicketServiceTest {

    private TicketMapper mapper;
    private TicketServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(TicketMapper.class);
        service = new TicketServiceImpl(mapper);
    }

    @Test
    void movesTicketForwardWithOptimisticStatusCheck() {
        TicketModels.AdminTicketView open = ticket("OPEN");
        TicketModels.AdminTicketView assigned = ticket("ASSIGNED");
        when(mapper.findAdminById("ticket-1")).thenReturn(open, assigned);
        when(mapper.updateStatus("ticket-1", "OPEN", "ASSIGNED", "admin", "开始处理"))
                .thenReturn(1);

        assertThat(service.updateStatus("ticket-1", "ASSIGNED", "开始处理", "admin").status())
                .isEqualTo("ASSIGNED");
        verify(mapper).updateStatus("ticket-1", "OPEN", "ASSIGNED", "admin", "开始处理");
    }

    @Test
    void rejectsSkippedOrBackwardTransition() {
        when(mapper.findAdminById("ticket-1")).thenReturn(ticket("OPEN"));

        assertThatThrownBy(() -> service.updateStatus("ticket-1", "RESOLVED", null, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("按流程向后流转");
    }

    @Test
    void reportsConcurrentUpdate() {
        when(mapper.findAdminById("ticket-1")).thenReturn(ticket("ASSIGNED"));
        when(mapper.updateStatus("ticket-1", "ASSIGNED", "RESOLVED", "admin", "已处理"))
                .thenReturn(0);

        assertThatThrownBy(() -> service.updateStatus("ticket-1", "RESOLVED", "已处理", "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("其他客服");
    }

    @Test
    void createsIdempotentActionTicketAndListsCustomerTickets() {
        TicketModels.TicketView existing = new TicketModels.TicketView("ticket-2", "问题", "描述", "OPEN", Instant.now());
        when(mapper.findByAction("action-1", 1L)).thenReturn(existing);
        assertThat(service.createFromAction("action-1", 1L, "session-1", "问题", "描述"))
                .isSameAs(existing);
        verify(mapper, never()).insert(anyString(), anyLong(), anyString(), any(), anyString(), anyString());

        when(mapper.findByCustomer(1L)).thenReturn(java.util.List.of(existing));
        assertThat(service.list(1L)).containsExactly(existing);
    }

    @Test
    void validatesAdminFiltersAndMissingTicket() {
        when(mapper.findForAdmin("OPEN")).thenReturn(java.util.List.of());
        assertThat(service.listForAdmin("OPEN")).isEmpty();
        assertThat(service.listForAdmin(null)).isEmpty();
        verify(mapper).findForAdmin("OPEN");
        verify(mapper).findForAdmin(null);

        assertThatThrownBy(() -> service.listForAdmin("INVALID"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("无效工单状态");
        when(mapper.findAdminById("missing")).thenReturn(null);
        assertThatThrownBy(() -> service.updateStatus("missing", "ASSIGNED", null, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("工单不存在");
    }

    @Test
    void rejectsOversizedResolutionNoteAndClosedTransition() {
        when(mapper.findAdminById("ticket-3")).thenReturn(ticket("ASSIGNED"));
        assertThatThrownBy(() -> service.updateStatus("ticket-3", "RESOLVED", "x".repeat(501), "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不能超过");

        when(mapper.findAdminById("ticket-4")).thenReturn(ticket("CLOSED"));
        assertThatThrownBy(() -> service.updateStatus("ticket-4", "ASSIGNED", null, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("按流程");
    }

    private TicketModels.AdminTicketView ticket(String status) {
        return new TicketModels.AdminTicketView(
                "ticket-1",
                1L,
                "演示客户",
                "session-1",
                "客户投诉",
                "描述",
                status,
                null,
                null,
                Instant.now(),
                Instant.now(),
                null);
    }

    @Test
    void repliesEnforceOwnershipAndReplayOnlyMatchingMessages() {
        String key = java.util.UUID.randomUUID().toString();
        when(mapper.lock("ticket-1")).thenReturn(ticket("ASSIGNED"));
        var original = new TicketModels.Message(key, "CUSTOMER", "问题", Instant.now());
        when(mapper.message("ticket-1", key)).thenReturn(original);
        assertThat(service.reply("ticket-1", 1L, key, "问题")).isSameAs(original);
        assertThatThrownBy(() -> service.reply("ticket-1", 2L, key, "问题")).hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.reply("ticket-1", 1L, key, "其他问题")).hasMessageContaining("其他内容");
        assertThatThrownBy(() -> service.reply("ticket-1", null, key, "问题")).hasMessageContaining("其他内容");
        assertThatThrownBy(() -> service.reply("ticket-1", 1L, "bad", "问题")).hasMessageContaining("无效");
        when(mapper.lock("ticket-1")).thenReturn(ticket("CLOSED"));
        assertThatThrownBy(() -> service.reply(
                        "ticket-1", 1L, java.util.UUID.randomUUID().toString(), "新问题"))
                .hasMessageContaining("已关闭");
    }

    @Test
    void customerReplyReopensAndClosingRequiresResolvedOwnedTicket() {
        String key = java.util.UUID.randomUUID().toString();
        when(mapper.lock("ticket-1")).thenReturn(ticket("RESOLVED"));
        service.reply("ticket-1", 1L, key, "仍需处理");
        verify(mapper).reopen("ticket-1");
        service.closeByCustomer("ticket-1", 1);
        verify(mapper).updateStatus("ticket-1", "RESOLVED", "CLOSED", "customer", null);
        assertThatThrownBy(() -> service.closeByCustomer("ticket-1", 2)).hasMessageContaining("不存在");
        when(mapper.lock("ticket-1")).thenReturn(ticket("OPEN"));
        assertThatThrownBy(() -> service.closeByCustomer("ticket-1", 1)).hasMessageContaining("解决后");
        when(mapper.lock("ticket-1")).thenReturn(ticket("CLOSED"));
        service.closeByCustomer("ticket-1", 1);
    }

    @Test
    void detailExposesPublicConversationWithoutInternalResolution() {
        var view = new TicketModels.TicketView("ticket-1", "主题", "描述", "OPEN", Instant.now());
        when(mapper.findOwned("ticket-1", 1)).thenReturn(view);
        when(mapper.messages("ticket-1")).thenReturn(java.util.List.of());
        assertThat(service.detail("ticket-1", 1L).ticket()).isEqualTo(view);
        assertThatThrownBy(() -> service.detail("ticket-1", 2L)).hasMessageContaining("不存在");
        when(mapper.findAdminById("ticket-1")).thenReturn(ticket("OPEN"));
        assertThat(service.detail("ticket-1", null).ticket().publicId()).isEqualTo("ticket-1");
        assertThatThrownBy(() -> service.detail("missing", null)).hasMessageContaining("不存在");
    }

    @Test
    void publishesOnlyExplicitCustomerMessages() {
        when(mapper.lock("ticket-1")).thenReturn(ticket("ASSIGNED"));
        when(mapper.publishCustomerMessage("ticket-1", "已核验")).thenReturn(1);
        service.publishCustomerMessage("ticket-1", " 已核验 ");
        verify(mapper).publishCustomerMessage("ticket-1", "已核验");
        assertThatThrownBy(() -> service.publishCustomerMessage("missing", "进度"))
                .hasMessageContaining("不存在");
        for (String invalid : new String[] {null, " ", "x".repeat(501)}) {
            assertThatThrownBy(() -> service.publishCustomerMessage("ticket-1", invalid))
                    .hasMessageContaining("500");
        }
    }
}
