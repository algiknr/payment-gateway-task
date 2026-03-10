package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.client.BankSimulatorClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for PaymentGatewayService.
 *
 * Uses Mockito to isolate the service from external dependencies (bank simulator and repository).
 * Each test verifies:
 *   - The correct payment status is returned
 *   - The request forwarded to the bank contains the expected fields
 *   - The response persisted in the repository has the correct masked card and payment details
 *
 * Card number convention (bank simulator rules):
 *   - Odd last digit  → bank returns authorized
 *   - Even last digit → bank returns declined
 *   - Zero last digit → bank returns 503 error
 */
@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock
  private PaymentsRepository paymentsRepository;

  @Mock
  private BankSimulatorClient bankSimulatorClient;

  @InjectMocks
  private PaymentGatewayService service;

  /** Captures the request object sent to the bank simulator for field-level verification. */
  @Captor
  private ArgumentCaptor<BankPaymentRequest> bankRequestCaptor;

  /** Captures the response object saved to the repository for field-level verification. */
  @Captor
  private ArgumentCaptor<PostPaymentResponse> savedResponseCaptor;

  /** Builds a valid payment request with a future expiry date. Card number determines bank behavior. */
  private PostPaymentRequest buildRequest(String cardNumber) {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber(cardNumber);
    request.setExpiryMonth(YearMonth.now().plusMonths(1).getMonthValue());
    request.setExpiryYear(YearMonth.now().plusMonths(1).getYear());
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }

  /**
   * Verifies a successful payment flow: bank authorizes → status is AUTHORIZED,
   * all card/amount fields are correctly forwarded to the bank and persisted.
   */
  @Test
  void testProcessPayment_Authorized() {
    PostPaymentRequest request = buildRequest("2222405343248877"); // last digit 7 (odd) → authorized
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    when(bankSimulatorClient.processPayment(any(BankPaymentRequest.class))).thenReturn(bankResponse);

    PostPaymentResponse response = service.processPayment(request);

    assertNotNull(response.getId());
    assertEquals(PaymentStatus.AUTHORIZED, response.getStatus());

    verify(bankSimulatorClient).processPayment(bankRequestCaptor.capture());
    BankPaymentRequest sentToBank = bankRequestCaptor.getValue();
    assertEquals("2222405343248877", sentToBank.getCardNumber());
    assertEquals(request.getExpiryDate(), sentToBank.getExpiryDate());
    assertEquals("USD", sentToBank.getCurrency());
    assertEquals(100, sentToBank.getAmount());
    assertEquals("123", sentToBank.getCvv());

    verify(paymentsRepository).add(savedResponseCaptor.capture());
    PostPaymentResponse saved = savedResponseCaptor.getValue();
    assertEquals(PaymentStatus.AUTHORIZED, saved.getStatus());
    assertEquals(8877, saved.getCardNumberLastFour());
    assertEquals(request.getExpiryMonth(), saved.getExpiryMonth());
    assertEquals(request.getExpiryYear(), saved.getExpiryYear());
    assertEquals("USD", saved.getCurrency());
    assertEquals(100, saved.getAmount());
  }

  /**
   * Verifies a declined payment flow: bank explicitly rejects → status is DECLINED,
   * all fields are still correctly forwarded and persisted.
   */
  @Test
  void testProcessPayment_Declined() {
    PostPaymentRequest request = buildRequest("2222405343248878"); // last digit 8 (even) → declined
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(false);
    when(bankSimulatorClient.processPayment(any(BankPaymentRequest.class))).thenReturn(bankResponse);

    PostPaymentResponse response = service.processPayment(request);

    assertNotNull(response.getId());
    assertEquals(PaymentStatus.DECLINED, response.getStatus());

    verify(bankSimulatorClient).processPayment(bankRequestCaptor.capture());
    BankPaymentRequest sentToBank = bankRequestCaptor.getValue();
    assertEquals("2222405343248878", sentToBank.getCardNumber());
    assertEquals(request.getExpiryDate(), sentToBank.getExpiryDate());
    assertEquals("USD", sentToBank.getCurrency());
    assertEquals(100, sentToBank.getAmount());
    assertEquals("123", sentToBank.getCvv());

    verify(paymentsRepository).add(savedResponseCaptor.capture());
    PostPaymentResponse saved = savedResponseCaptor.getValue();
    assertEquals(PaymentStatus.DECLINED, saved.getStatus());
    assertEquals(8878, saved.getCardNumberLastFour());
    assertEquals(request.getExpiryMonth(), saved.getExpiryMonth());
    assertEquals(request.getExpiryYear(), saved.getExpiryYear());
    assertEquals("USD", saved.getCurrency());
    assertEquals(100, saved.getAmount());
  }

  /**
   * Verifies graceful degradation when the bank simulator is unreachable or throws an error.
   * The payment should still be saved as DECLINED rather than leaving it in an unknown state.
   */
  @Test
  void testProcessPayment_BankError() {
    PostPaymentRequest request = buildRequest("2222405343248870"); // last digit 0 → bank 503 error
    when(bankSimulatorClient.processPayment(any())).thenThrow(new RuntimeException("Bank down"));

    PostPaymentResponse response = service.processPayment(request);

    assertEquals(PaymentStatus.DECLINED, response.getStatus());

    verify(bankSimulatorClient).processPayment(bankRequestCaptor.capture());
    BankPaymentRequest sentToBank = bankRequestCaptor.getValue();
    assertEquals("2222405343248870", sentToBank.getCardNumber());
    assertEquals(request.getExpiryDate(), sentToBank.getExpiryDate());
    assertEquals("USD", sentToBank.getCurrency());
    assertEquals(100, sentToBank.getAmount());
    assertEquals("123", sentToBank.getCvv());

    verify(paymentsRepository).add(savedResponseCaptor.capture());
    PostPaymentResponse saved = savedResponseCaptor.getValue();
    assertEquals(PaymentStatus.DECLINED, saved.getStatus());
    assertEquals(8870, saved.getCardNumberLastFour());
    assertEquals(request.getExpiryMonth(), saved.getExpiryMonth());
    assertEquals(request.getExpiryYear(), saved.getExpiryYear());
    assertEquals("USD", saved.getCurrency());
    assertEquals(100, saved.getAmount());
  }

  /**
   * Verifies that a request with a past expiry date is rejected before reaching the bank.
   * Neither the bank simulator nor the repository should be invoked.
   */
  @Test
  void testProcessPayment_InvalidExpiry() {
    PostPaymentRequest validRequest = buildRequest("2222405343248877");
    validRequest.setExpiryYear(2020);

    Exception ex = assertThrows(IllegalArgumentException.class,
        () -> service.processPayment(validRequest));
    assertTrue(ex.getMessage().contains("Expiry date is in the past"));

    verifyNoInteractions(bankSimulatorClient);
    verifyNoInteractions(paymentsRepository);
  }
}
