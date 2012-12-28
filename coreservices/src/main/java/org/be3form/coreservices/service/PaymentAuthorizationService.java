package org.be3form.coreservices.service;

import org.be3form.coreservices.data.Payment;

public interface PaymentAuthorizationService {
  void authorize(Payment p);
}
