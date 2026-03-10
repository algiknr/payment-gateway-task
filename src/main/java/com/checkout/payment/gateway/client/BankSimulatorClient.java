package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class BankSimulatorClient {

  private static final Logger LOG = LoggerFactory.getLogger(BankSimulatorClient.class);

  private final RestTemplate restTemplate;
  private final String bankSimulatorUrl;

  public BankSimulatorClient(RestTemplate restTemplate, @Value("${bank.simulator.url:http://localhost:8080/payments}") String bankSimulatorUrl) {
    this.restTemplate = restTemplate;
    this.bankSimulatorUrl = bankSimulatorUrl;
  }

  public BankPaymentResponse processPayment(BankPaymentRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<BankPaymentRequest> entity = new HttpEntity<>(request, headers);

    try {
      LOG.info("Calling Bank Simulator at {}", bankSimulatorUrl);
      ResponseEntity<BankPaymentResponse> response = restTemplate.postForEntity(
          bankSimulatorUrl, entity, BankPaymentResponse.class);
      return response.getBody();
    } catch (RestClientException ex) {
      LOG.error("Bank Simulator error: {}", ex.getMessage());
      throw ex;
    }
  }
}
