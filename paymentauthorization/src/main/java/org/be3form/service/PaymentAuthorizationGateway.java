package org.be3form.service;

import java.util.concurrent.Future;

import org.be3form.coreservices.data.Payment;

/**
 * Interface for gateway to put messages to outbound channel
 * It is defined as spring integration gateway and could be referenced from another beans.
 * Because we want to send message as async we have to define that return type is Future
 */
public interface PaymentAuthorizationGateway {
  Future<Payment> sendAuthorizationRequest(Payment payment);
}
