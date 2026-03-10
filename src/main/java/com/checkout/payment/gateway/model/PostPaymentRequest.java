package com.checkout.payment.gateway.model;

import java.io.Serializable;
import java.time.YearMonth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class PostPaymentRequest implements Serializable {

  @JsonProperty("card_number")
  @NotBlank
  @Pattern(regexp = "^[0-9]{14,19}$", message = "Card number must be 14-19 digits")
  private String cardNumber;

  @JsonProperty("expiry_month")
  @NotNull
  @Min(value = 1, message = "Expiry month must be between 1 and 12")
  @Max(value = 12, message = "Expiry month must be between 1 and 12")
  private Integer expiryMonth;

  @JsonProperty("expiry_year")
  @NotNull
  private Integer expiryYear;

  @NotBlank
  @Pattern(regexp = "^(USD|EUR|GBP)$", message = "Currency must be one of USD, EUR, GBP")
  private String currency;

  @NotNull
  @Min(value = 0, message = "Amount must be a positive integer")
  private Integer amount;

  @NotBlank
  @Pattern(regexp = "^[0-9]{3,4}$", message = "CVV must be 3-4 digits")
  private String cvv;

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public Integer getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(Integer expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public Integer getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(Integer expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Integer getAmount() {
    return amount;
  }

  public void setAmount(Integer amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }

  @JsonIgnore
  public String getExpiryDate() {
    return String.format("%02d/%d", expiryMonth, expiryYear);
  }

  @JsonIgnore
  public boolean isExpiryDateValid() {
    if (expiryYear == null || expiryMonth == null) {
      return false;
    }
    YearMonth currentMonth = YearMonth.now();
    YearMonth cardExpiry = YearMonth.of(expiryYear, expiryMonth);
    return !cardExpiry.isBefore(currentMonth);
  }

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "cardNumber='********" + (cardNumber != null && cardNumber.length() > 4 ? cardNumber.substring(cardNumber.length() - 4) : "") + '\'' +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", cvv=" + cvv +
        '}';
  }
}
