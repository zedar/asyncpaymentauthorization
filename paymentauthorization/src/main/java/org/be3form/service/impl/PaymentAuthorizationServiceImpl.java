package org.be3form.service.impl;

import java.io.IOException;
import java.io.PrintWriter;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.servlet.AsyncContext;

import org.be3form.coreservices.data.Payment;

import org.be3form.service.PaymentAuthorizationGateway;
import org.be3form.service.PaymentAuthorizationService;

/**
 *  Singleton for sending authorization request and handling async response.
 *  
 */
@Service("paymentAuthorizationService")
public class PaymentAuthorizationServiceImpl implements PaymentAuthorizationService {
  private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizationServiceImpl.class);
  
  // map of all requests sent to coreservices
  ConcurrentHashMap<String, AsyncContext> correlations = new ConcurrentHashMap<String, AsyncContext>();

  @Autowired
  PaymentAuthorizationGateway payAuthGateway2CoreServices;

  /**
   *  Send async request, store context in correlations map and return immediatelly
   */
  @Override
  public void sendPaymentAuthorizationRequest(Payment payment, AsyncContext asyncContext) {
    if (payment == null || asyncContext == null) {
      log.error("Wrong input attributes: payment or asyncContext");
      return;
    }
    log.debug("*** START SENDING ASYNC PAYMENT REQUEST: " + payment.toString());
    if (correlations == null) {
      log.debug("*** CORRELATIONS HASHMAP IS NULL ***");
      return;
    }
    correlations.put(payment.getId(), asyncContext);
    log.debug("*** CORRELATION STORED ***");
    payAuthGateway2CoreServices.sendAuthorizationRequest(payment);
    log.debug("*** REQUEST SENT ***");
  }

  /**
   *  Get async authorization response, correlate with servlet's async context and finish processing
   */
  @Override
  public void handlePaymentAuthorizationResponse(Payment payment) {
    if (payment == null) {
      log.error("Unknown payment to correlate");
      return;
    }
    log.debug("*** RECEIVED PAYMENT AUTHORIZATION RESPONSE: " + payment.toString());
    log.debug("*** GET ASYNC CONTEXT FROM CORRELATIONS ***");
    AsyncContext actx = correlations.get(payment.getId());
    if (actx == null) {
      log.error("AsyncContext not found in correlations");
      return;
    }

    ServletResponse response = actx.getResponse();
    response.setContentType("text/html");
    try {
      PrintWriter writer = response.getWriter();
      writer.write("<h1>Spring Beans Injection into Java Servlets!</h1><h2>" + "Request Id=" + payment.getId() + "</h2>");
    }
    catch(Exception ex) {
      ex.printStackTrace();
      return;
    }
    actx.complete();
    log.debug("*** Response passed to servlet ***");
    correlations.remove(payment.getId());
    log.debug("*** AsyncContext removed from collections ***");
  }
}
