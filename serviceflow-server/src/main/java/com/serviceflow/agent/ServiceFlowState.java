package com.serviceflow.agent;

import com.serviceflow.auth.CurrentPrincipal;
import java.util.ArrayList;
import java.util.List;

public final class ServiceFlowState {
    private final String sessionId;
    private final CurrentPrincipal principal;
    private final String query;
    private final Long pageProductId;
    private final String clientRequestId;
    private Intent intent;
    private final List<Long> productIds = new ArrayList<>();
    private String activeOrderNo;
    private int retryCount;

    public ServiceFlowState(String sessionId, CurrentPrincipal principal, String query, Long pageProductId) {
        this(sessionId, principal, query, pageProductId, null);
    }

    public ServiceFlowState(
            String sessionId, CurrentPrincipal principal, String query, Long pageProductId, String clientRequestId) {
        this.sessionId = sessionId;
        this.principal = principal;
        this.query = query;
        this.pageProductId = pageProductId;
        this.clientRequestId = clientRequestId;
    }

    public String sessionId() {
        return sessionId;
    }

    public CurrentPrincipal principal() {
        return principal;
    }

    public String query() {
        return query;
    }

    public Long pageProductId() {
        return pageProductId;
    }

    public String clientRequestId() {
        return clientRequestId;
    }

    public Intent intent() {
        return intent;
    }

    public void intent(Intent intent) {
        this.intent = intent;
    }

    public List<Long> productIds() {
        return productIds;
    }

    public String activeOrderNo() {
        return activeOrderNo;
    }

    public void activeOrderNo(String value) {
        activeOrderNo = value;
    }

    public int retryCount() {
        return retryCount;
    }

    public void incrementRetry() {
        retryCount++;
    }
}
