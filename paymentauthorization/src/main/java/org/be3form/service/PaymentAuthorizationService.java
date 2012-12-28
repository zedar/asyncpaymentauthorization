package org.be3form.service;

import javax.servlet.AsyncContext;

import org.be3form.coreservices.data.Payment;

/**
 * Spring bean (signleton) for sending payment authorizations and receiving payment authorization responses.
 * Internally it keeps calling servlet's conetext, in order pass http response.
 */
public interface PaymentAuthorizationService {
  /**
   * Send payment authorization to the backend system (coreservices)
   */
  void sendPaymentAuthorizationRequest(Payment payment, AsyncContext asyncContext);

  /**
   * Handle payment authorization response
   */
  void handlePaymentAuthorizationResponse(Payment payment);
}
