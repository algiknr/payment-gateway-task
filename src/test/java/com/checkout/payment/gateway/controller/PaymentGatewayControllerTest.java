package com.checkout.payment.gateway.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import com.checkout.payment.gateway.model.PostPaymentRequest;

/**
 * Integration tests for PaymentGatewayController.
 *
 * Loads the full Spring Boot context and uses MockMvc to send HTTP requests
 * against the real controller and repository (no mocks).
 * Verifies endpoint behavior including status codes, JSON response structure, and error handling.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  PaymentsRepository paymentsRepository;
  
  // ── Helper Methods ─────────────────────────────────────────────────────────

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** Builds a valid request JSON with overridable fields. */
  private String validRequestJson() throws Exception {
    PostPaymentRequest req = new PostPaymentRequest();
    req.setCardNumber("22224053432488770");
    req.setExpiryMonth(YearMonth.now().plusMonths(1).getMonthValue());
    req.setExpiryYear(YearMonth.now().plusMonths(1).getYear());
    req.setCurrency("GBP");
    req.setAmount(100);
    req.setCvv("123");
    return MAPPER.writeValueAsString(req);
  }

  private void postAndExpectRejected(String json) throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(PaymentStatus.REJECTED.getName()));
  }
  
  private void postAndExpectSuccess(String json) throws Exception {
	mvc.perform(MockMvcRequestBuilders.post("/payment")
	    .contentType(MediaType.APPLICATION_JSON)
	    .content(json))
	    .andExpect(status().isCreated());
  }
  
//─────────────────────────────────────────────────────────────────────────────── 

  /**
   * GET /payment/{id} — when a payment exists in the repository,
   * the endpoint should return 200 OK with all payment details
   * (status, masked card number, expiry, currency, amount).
   */
  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour(4321);

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  /**
   * GET /payment/{id} — when no payment exists for the given id,
   * the endpoint should return 404 Not Found with an appropriate error message.
   */
  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Payment not found"));
  }

  /** GET /payment/{id} with a non-UUID string should return 400 Bad Request. */
  @Test
  void getPayment_invalidUuidFormat_returns400() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payment/not-a-valid-uuid"))
        .andExpect(status().isBadRequest());
  }

//─────────────────────────────────────────────────────────────────────────────── 

  /**
   * POST /payment — when a request with missing required fields is submitted,
   * validation should fail and the endpoint should return 400 Bad Request
   * with a REJECTED status, without forwarding anything to the bank.
   */
  @Test
  void whenInvalidPaymentIsPosted_thenRejected() throws Exception {
    PostPaymentRequest request = new PostPaymentRequest();
    
    String json = MAPPER.writeValueAsString(request);
    postAndExpectRejected(json);
  }

  /**
   * POST /payment — valid request should return 201 Created with a generated UUID,
   * correct status, masked card number, and all payment details.
   */
  @Test
  void validPayment_returnsCreatedWithAllFields() throws Exception {
    String json = validRequestJson();
    YearMonth nextMonth = YearMonth.now().plusMonths(1);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.status").isNotEmpty())
        .andExpect(jsonPath("$.cardNumberLastFour").value(8770))
        .andExpect(jsonPath("$.expiryMonth").value(nextMonth.getMonthValue()))
        .andExpect(jsonPath("$.expiryYear").value(nextMonth.getYear()))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100));
  }

  // ── Card Number Edge Cases ─────────────────────────────────────────

  /** Card number with exactly 14 digits (minimum) should be accepted. */
  @Test
  void cardNumber_14digits_isAccepted() throws Exception {
    String json = validRequestJson().replace("22224053432488770", "22224053432488");
    postAndExpectSuccess(json);
  }

  /** Card number with exactly 19 digits (maximum) should be accepted. */
  @Test
  void cardNumber_19digits_isAccepted() throws Exception {
    String json = validRequestJson().replace("22224053432488770", "2222405343248877012");
    postAndExpectSuccess(json);
  }

  /** Card number with 13 digits (below minimum) should be rejected. */
  @Test
  void cardNumber_13digits_isRejected() throws Exception {
    String json = validRequestJson().replace("22224053432488770", "2222405343248");
    postAndExpectRejected(json);
  }

  /** Card number with 20 digits (above maximum) should be rejected. */
  @Test
  void cardNumber_20digits_isRejected() throws Exception {
    String json = validRequestJson().replace("22224053432488770", "22224053432488770123");
    postAndExpectRejected(json);
  }

  /** Card number with non-numeric characters should be rejected. */
  @Test
  void cardNumber_nonNumeric_isRejected() throws Exception {
    String json = validRequestJson().replace("22224053432488770", "2222ABCD43248877");
    postAndExpectRejected(json);
  }

  // ── CVV Edge Cases ─────────────────────────────────────────────────

  /** CVV with 3 digits should be accepted. */
  @Test
  void cvv_3digits_isAccepted() throws Exception {
    String json = validRequestJson();
    postAndExpectSuccess(json);
  }

  /** CVV with 4 digits should be accepted. */
  @Test
  void cvv_4digits_isAccepted() throws Exception {
    String json = validRequestJson().replace("\"123\"", "\"1234\"");
    postAndExpectSuccess(json);
  }

  /** CVV with 2 digits (too short) should be rejected. */
  @Test
  void cvv_2digits_isRejected() throws Exception {
    String json = validRequestJson().replace("\"123\"", "\"12\"");
    postAndExpectRejected(json);
  }

  /** CVV with 5 digits (too long) should be rejected. */
  @Test
  void cvv_5digits_isRejected() throws Exception {
    String json = validRequestJson().replace("\"123\"", "\"12345\"");
    postAndExpectRejected(json);
  }

  // ── Currency Edge Cases ────────────────────────────────────────────

  /** Unsupported currency should be rejected. */
  @Test
  void currency_unsupported_isRejected() throws Exception {
    String json = validRequestJson().replace("\"GBP\"", "\"JPY\"");
    postAndExpectRejected(json);
  }

  // ── Amount Edge Cases ──────────────────────────────────────────────

  /** Negative amount should be rejected. */
  @Test
  void amount_negative_isRejected() throws Exception {
    String json = validRequestJson().replace(":100", ":-5");
    postAndExpectRejected(json);
  }

  // ── Expiry Month Edge Cases ────────────────────────────────────────

  /** Expiry month 0 (below minimum) should be rejected. */
  @Test
  void expiryMonth_zero_isRejected() throws Exception {
    String json = validRequestJson();
    json = json.replaceFirst("\"expiry_month\":\\d+", "\"expiry_month\":0");
    postAndExpectRejected(json);
  }

  /** Expiry month 13 (above maximum) should be rejected. */
  @Test
  void expiryMonth_13_isRejected() throws Exception {
    String json = validRequestJson();
    json = json.replaceFirst("\"expiry_month\":\\d+", "\"expiry_month\":13");
    postAndExpectRejected(json);
  }

  // ── Expiry Date Edge Cases ─────────────────────────────────────────

  /** Expiry date set to current month should be accepted (not yet expired). */
  @Test
  void expiryDate_currentMonth_isAccepted() throws Exception {
    YearMonth now = YearMonth.now();
    String json = validRequestJson();
    json = json.replaceFirst("\"expiry_month\":\\d+", "\"expiry_month\":" + now.getMonthValue());
    json = json.replaceFirst("\"expiry_year\":\\d+", "\"expiry_year\":" + now.getYear());
    postAndExpectSuccess(json);
  }

  /** Expiry date set to last month should be rejected (expired). */
  @Test
  void expiryDate_lastMonth_isRejected() throws Exception {
    YearMonth lastMonth = YearMonth.now().minusMonths(1);
    String json = validRequestJson();
    json = json.replaceFirst("\"expiry_month\":\\d+", "\"expiry_month\":" + lastMonth.getMonthValue());
    json = json.replaceFirst("\"expiry_year\":\\d+", "\"expiry_year\":" + lastMonth.getYear());
    postAndExpectRejected(json);
  }

}
