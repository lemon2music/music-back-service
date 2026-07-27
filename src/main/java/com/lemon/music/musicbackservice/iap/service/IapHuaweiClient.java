package com.lemon.music.musicbackservice.iap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemon.music.musicbackservice.common.BusinessException;
import com.lemon.music.musicbackservice.iap.dto.response.HuaweiOrderStatusResponse;
import com.lemon.music.musicbackservice.iap.dto.response.HuaweiSimpleResponse;
import com.lemon.music.musicbackservice.iap.dto.response.HuaweiSubStatusResponse;
import com.lemon.music.musicbackservice.iap.support.IapJwtGenerator;
import com.lemon.music.musicbackservice.iap.support.IapProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 调用华为 IAP REST API（订单/订阅 查询、确认发货），请求带 ES256 JWT。 */
@Slf4j
@Component
public class IapHuaweiClient {

    private static final String URL_ORDER_STATUS_QUERY = "/order/harmony/v1/application/order/status/query";
    private static final String URL_ORDER_SHIPPED_CONFIRM = "/order/harmony/v1/application/purchase/shipped/confirm";
    private static final String URL_SUB_STATUS_QUERY = "/subscription/harmony/v1/application/subscription/status/query";
    private static final String URL_SUB_SHIPPED_CONFIRM = "/subscription/harmony/v1/application/purchase/shipped/confirm";

    private final RestClient restClient;
    private final IapJwtGenerator jwtGenerator;
    private final ObjectMapper objectMapper;

    public IapHuaweiClient(RestClient.Builder restClientBuilder,
                           IapProperties properties,
                           IapJwtGenerator jwtGenerator,
                           ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.jwtGenerator = jwtGenerator;
        this.objectMapper = objectMapper;
    }

    public HuaweiOrderStatusResponse orderStatusQuery(String purchaseOrderId, String purchaseToken) {
        return post(URL_ORDER_STATUS_QUERY,
                Map.of("purchaseOrderId", purchaseOrderId, "purchaseToken", purchaseToken),
                HuaweiOrderStatusResponse.class);
    }

    public boolean orderShippedConfirm(String purchaseOrderId, String purchaseToken) {
        HuaweiSimpleResponse r = post(URL_ORDER_SHIPPED_CONFIRM,
                Map.of("purchaseOrderId", purchaseOrderId, "purchaseToken", purchaseToken), HuaweiSimpleResponse.class);
        return "0".equals(r.responseCode());
    }

    public HuaweiSubStatusResponse subStatusQuery(String purchaseOrderId, String purchaseToken) {
        return post(URL_SUB_STATUS_QUERY,
                Map.of("purchaseOrderId", purchaseOrderId, "purchaseToken", purchaseToken),
                HuaweiSubStatusResponse.class);
    }

    public boolean subShippedConfirm(String purchaseOrderId, String purchaseToken) {
        HuaweiSimpleResponse r = post(URL_SUB_SHIPPED_CONFIRM,
                Map.of("purchaseOrderId", purchaseOrderId, "purchaseToken", purchaseToken), HuaweiSimpleResponse.class);
        return "0".equals(r.responseCode());
    }

    private <T> T post(String path, Map<String, Object> body, Class<T> type) {
        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new BusinessException("构造华为请求体失败");
        }
        String jwt = jwtGenerator.generate(bodyJson);
        try {
            return restClient.post()
                    .uri(path)
                    .header("Authorization", "Bearer " + jwt)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body(bodyJson)
                    .retrieve()
                    .body(type);
        } catch (Exception e) {
            log.error("调用华为 IAP REST 失败: {}", path, e);
            throw new BusinessException("调用华为 IAP 接口失败: " + path);
        }
    }
}
