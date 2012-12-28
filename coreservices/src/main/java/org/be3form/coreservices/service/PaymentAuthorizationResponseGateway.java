package org.be3form.coreservices.service;

import java.util.concurrent.Future;

import org.be3form.coreservices.data.Payment;

/**
 *  Interface to be a gateway for sending payment authorization responses.
 *  It is defined as spring integration gateway to JMS messaging.
 *  Because we want to send response messages as async we define return value as Future.
 */
public interface PaymentAuthorizationResponseGateway {
  Future<Payment> sendAuthorizationResponse(Payment payment);
}
