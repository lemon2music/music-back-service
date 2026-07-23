package com.lemon.music.musicbackservice.iap.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemon.music.musicbackservice.iap.dto.payload.NotificationPayload;
import com.lemon.music.musicbackservice.iap.dto.payload.PurchaseData;
import com.lemon.music.musicbackservice.iap.dto.payload.PurchaseOrderPayload;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IapPayloadMappingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void iapProductTypeFromCode() {
        assertThat(IapProductType.fromCode(0)).isEqualTo(IapProductType.CONSUMABLE);
        assertThat(IapProductType.fromCode(2)).isEqualTo(IapProductType.AUTORENEWABLE);
        assertThat(IapProductType.AUTORENEWABLE.getCode()).isEqualTo(2);
    }

    @Test
    void purchaseDataParses() throws Exception {
        String json = "{\"type\":0,\"jwsPurchaseOrder\":\"abc.jwt.def\"}";
        PurchaseData data = mapper.readValue(json, PurchaseData.class);
        assertThat(data.type()).isEqualTo(0);
        assertThat(data.jwsPurchaseOrder()).isEqualTo("abc.jwt.def");
    }

    @Test
    void purchaseOrderPayloadParses() throws Exception {
        String json = "{\"productId\":\"P1\",\"purchaseOrderId\":\"PO1\",\"purchaseToken\":\"T1\","
                + "\"developerPayload\":\"ORDER_NO_123\",\"finishStatus\":\"2\","
                + "\"purchaseOrderRevocationReasonCode\":null,\"productType\":0}";
        PurchaseOrderPayload p = mapper.readValue(json, PurchaseOrderPayload.class);
        assertThat(p.productId()).isEqualTo("P1");
        assertThat(p.developerPayload()).isEqualTo("ORDER_NO_123");
        assertThat(p.finishStatus()).isEqualTo("2");
    }

    @Test
    void notificationPayloadParses() throws Exception {
        String json = "{\"notificationType\":\"REVOKE\",\"notificationSubtype\":\"REFUND_TRANSACTION\","
                + "\"notificationRequestId\":\"REQ1\",\"notificationVersion\":\"v1\",\"signedTime\":1,"
                + "\"notificationMetaData\":{\"type\":0,\"currentProductId\":\"P1\","
                + "\"purchaseOrderId\":\"PO1\",\"purchaseToken\":\"T1\"}}";
        NotificationPayload n = mapper.readValue(json, NotificationPayload.class);
        assertThat(n.notificationType()).isEqualTo("REVOKE");
        assertThat(n.notificationMetaData().purchaseOrderId()).isEqualTo("PO1");
    }
}
