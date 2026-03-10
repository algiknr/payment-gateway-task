package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.BankSimulatorClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Core service that orchestrates payment processing between the merchant and the acquiring bank.
 * Validates incoming requests, forwards them to the bank simulator, and persists the outcome.
 */
@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final BankSimulatorClient bankSimulatorClient;

  public PaymentGatewayService(PaymentsRepository paymentsRepository, BankSimulatorClient bankSimulatorClient) {
    this.paymentsRepository = paymentsRepository;
    this.bankSimulatorClient = bankSimulatorClient;
  }

  /**
   * Retrieves a previously processed payment by its unique identifier.
   *
   * @param id the UUID assigned during payment processing
   * @return full payment details including masked card info and status
   * @throws EventProcessingException if no payment exists with the given id
   */
  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to payment with ID {}", id);
    return paymentsRepository.get(id).orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  /**
   * Processes a new payment request through the acquiring bank.
   *
   * Flow:
   * 1. Validates the card expiry date is not in the past
   * 2. Maps the merchant request to the bank's expected format
   * 3. Sends the request to the bank simulator
   * 4. Persists and returns the result with AUTHORIZED, DECLINED, or DECLINED (on bank error)
   *
   * If the bank simulator is unreachable or returns an error, the payment is saved as DECLINED
   * rather than leaving it in an unknown state.
   *
   * @param paymentRequest the merchant's payment request containing card and amount details
   * @return the persisted payment response with a generated id and resolved status
   * @throws IllegalArgumentException if the card expiry date is in the past
   */
  public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    if (!paymentRequest.isExpiryDateValid()) {
      throw new IllegalArgumentException("Expiry date is in the past");
    }

    BankPaymentRequest bankRequest = new BankPaymentRequest();
    bankRequest.setCardNumber(paymentRequest.getCardNumber());
    bankRequest.setExpiryDate(paymentRequest.getExpiryDate());
    bankRequest.setCurrency(paymentRequest.getCurrency());
    bankRequest.setAmount(paymentRequest.getAmount());
    bankRequest.setCvv(paymentRequest.getCvv());

    BankPaymentResponse bankResponse;
    try {
      bankResponse = bankSimulatorClient.processPayment(bankRequest);
    } catch (Exception e) {
      LOG.warn("Bank simulator error processing payment: {}", e.getMessage());
      return buildAndSaveResponse(paymentRequest, PaymentStatus.DECLINED);
    }

    PaymentStatus status = bankResponse.isAuthorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;
    return buildAndSaveResponse(paymentRequest, status);
  }

  /**
   * Builds a response from the original request data, assigns it a unique id,
   * masks the card number to its last four digits, and persists it to the repository.
   */
  private PostPaymentResponse buildAndSaveResponse(PostPaymentRequest request, PaymentStatus status) {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(UUID.randomUUID());
    response.setStatus(status);

    String cardNum = request.getCardNumber();
    if (cardNum != null && cardNum.length() >= 4) {
      response.setCardNumberLastFour(Integer.parseInt(cardNum.substring(cardNum.length() - 4)));
    }

    response.setExpiryMonth(request.getExpiryMonth());
    response.setExpiryYear(request.getExpiryYear());
    response.setCurrency(request.getCurrency());
    response.setAmount(request.getAmount());

    paymentsRepository.add(response);
    return response;
  }
}
