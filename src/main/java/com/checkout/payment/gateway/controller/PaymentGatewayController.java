package com.checkout.payment.gateway.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

@RestController("api")
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @Operation(summary = "Get payment by ID", description = "Retrieve a previously processed payment by its UUID")
  @ApiResponse(responseCode = "200", description = "Payment found",
      content = @Content(mediaType = "application/json",
          schema = @Schema(implementation = PostPaymentResponse.class),
          examples = @ExampleObject(value = "{\"id\":\"f1a2b3c4-d5e6-7890-abcd-ef1234567890\",\"status\":\"Authorized\",\"cardNumberLastFour\":8877,\"expiryMonth\":4,\"expiryYear\":2027,\"currency\":\"GBP\",\"amount\":100}")))
  @ApiResponse(responseCode = "404", description = "Payment not found",
      content = @Content(mediaType = "application/json",
          schema = @Schema(implementation = ErrorResponse.class),
          examples = @ExampleObject(value = "{\"message\":\"Payment not found\"}")))
  @GetMapping("/payment/{id}")
  public ResponseEntity<PostPaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
  }

  @Operation(summary = "Process a payment", description = "Submit a new payment for processing through the acquiring bank")
  @ApiResponse(responseCode = "201", description = "Payment processed",
      content = @Content(mediaType = "application/json",
          schema = @Schema(implementation = PostPaymentResponse.class),
          examples = @ExampleObject(value = "{\"id\":\"f1a2b3c4-d5e6-7890-abcd-ef1234567890\",\"status\":\"Authorized\",\"cardNumberLastFour\":8877,\"expiryMonth\":4,\"expiryYear\":2027,\"currency\":\"GBP\",\"amount\":100}")))
  @ApiResponse(responseCode = "400", description = "Validation error, payment rejected",
      content = @Content(mediaType = "application/json",
          schema = @Schema(implementation = PostPaymentResponse.class),
          examples = @ExampleObject(value = "{\"id\":null,\"status\":\"Rejected\",\"cardNumberLastFour\":0,\"expiryMonth\":0,\"expiryYear\":0,\"currency\":null,\"amount\":0}")))
  @PostMapping("/payment")
  public ResponseEntity<PostPaymentResponse> processPayment(@Valid @RequestBody PostPaymentRequest request) {
    return new ResponseEntity<>(paymentGatewayService.processPayment(request), HttpStatus.CREATED);
  }
}
