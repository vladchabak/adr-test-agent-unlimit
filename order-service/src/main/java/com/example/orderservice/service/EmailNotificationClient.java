package com.example.orderservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * External email notification service client.
 * Sends order confirmation and status update emails via SendGrid API.
 */
@Component
public class EmailNotificationClient {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationClient.class);

    private final RestTemplate restTemplate;
    private final String sendGridApiUrl;
    private final String sendGridApiKey;

    public EmailNotificationClient(
            RestTemplate restTemplate,
            @Value("${sendgrid.api.url}") String sendGridApiUrl,
            @Value("${sendgrid.api.key}") String sendGridApiKey) {
        this.restTemplate = restTemplate;
        this.sendGridApiUrl = sendGridApiUrl;
        this.sendGridApiKey = sendGridApiKey;
    }

    public void sendOrderConfirmationEmail(String customerEmail, String customerName, Long orderId) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("to", customerEmail);
            request.put("subject", "Order #" + orderId + " Confirmed");
            request.put("customer_name", customerName);

            var headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + sendGridApiKey);
            headers.set("Content-Type", "application/json");

            var entity = new org.springframework.http.HttpEntity<>(request, headers);
            restTemplate.postForObject(sendGridApiUrl + "/v3/mail/send", entity, Object.class);

            log.info("Sent confirmation email to {} for order {}", customerEmail, orderId);
        } catch (Exception e) {
            log.warn("Failed to send confirmation email for order {}: {}", orderId, e.getMessage());
            // Non-blocking: email failure doesn't block order creation
        }
    }
}
