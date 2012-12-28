package org.be3form.coreservices.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import org.be3form.coreservices.data.Payment;

import org.be3form.coreservices.service.PaymentAuthorizationService;
import org.be3form.coreservices.service.PaymentAuthorizationResponseGateway;

/**
 *  Core services implementation of payment authorizations. 
 */
@Service("paymentAuthorizationService")
public class PaymentAuthorizationServiceImpl implements PaymentAuthorizationService {
  private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizationServiceImpl.class);

  @Autowired
  PaymentAuthorizationResponseGateway payAuthResponseGateway;

  @Override
  public void authorize(Payment payment) {
    if (payment == null) {
      log.error("=== PAYMENT IS NOT GIVEN, STOP PROCESSING");
      return;
    }
    log.debug("=== PAYMENT AUTHORIZATION STARTED: " + payment.getId());

    // do some long running stuff here
    try {
      Thread.sleep(5000);
    }
    catch (Exception ex) {
      log.debug("Sleeping exception", ex);
      Thread.currentThread().interrupt();
    }
    // after that set authorization code
    payment.setAuthorizationResult(Payment.PAYAUTH_RESULT.AUTHORIZED);
    // set assigned NRB account
    payment.setAccountNumber("24251116757067918876746663");
    // send response via response gateway
    payAuthResponseGateway.sendAuthorizationResponse(payment);
    log.debug("=== PAYMENT AUTHORIZATION RESPONSE SENT: " + payment.getId());
  }
}
