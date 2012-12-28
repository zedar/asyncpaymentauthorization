package org.be3form.web.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import java.util.UUID;

import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncListener;
import javax.servlet.AsyncEvent;

import org.springframework.web.HttpRequestHandler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.stereotype.Service;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import org.be3form.service.PaymentAuthorizationService;
import org.be3form.coreservices.data.Payment;

/**
 *  Spring convention for implementation of servlet's logic.
 *  The idea is to use servlet 3.0 async functionality (AsyncContext) just in order to block (but only socket) and run all
 *  funtionality asynchrounsly.
 */
@Service("paymentAuthorizationHttpServletHandler")
public class PaymentAuthorizationHttpServletHandler implements HttpRequestHandler {
  private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizationHttpServletHandler.class);

  // request counter for servlet's instance - used mainly for debugging purposes
  private AtomicLong requestCounter = new AtomicLong();

  @Resource
  private ThreadPoolTaskExecutor paymentExecutor;

  @Autowired
  private PaymentAuthorizationService paymentAuthorizationService;

  @Override
  public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    // increment request counter
    long requestId = this.requestCounter.incrementAndGet();
    
    log.debug("===== START ASYNC TEST: [" + Long.toString(requestId) + "] =====");

    // start async context for the servlet
    AsyncContext actx = request.startAsync(request, response);
    // set timeout for servlet's request
    actx.setTimeout(50000);
    
    PaymentProxy paymentProxy = new PaymentProxy(actx, paymentAuthorizationService);
    actx.addListener(paymentProxy);

    paymentExecutor.submit(paymentProxy);
    
    log.debug("===== SERVLET THREAD PROCESSING [" + Long.toString(requestId) + "] =====");
  }

  // Void is defined with Java generics
  // This internal class is callable concurrent class (with call method) and implements servlet's 3 async listener services too
  private class PaymentProxy implements Callable<Void>, AsyncListener {
    private long requestId;

    private AsyncContext actx;

    private PaymentAuthorizationService paymentAuthorizationService;

    public PaymentProxy(AsyncContext actx, PaymentAuthorizationService paymentAuthorizationService) {
      this.actx = actx;
      this.requestId = requestId;
      this.paymentAuthorizationService = paymentAuthorizationService;
    }

    @Override
    public Void call() throws Exception {
      // create payment and send it to the coreservice backend for authorization
      log.debug("*** Payment authorization created! ***");

      // generate unique request ID
      UUID uuid = UUID.randomUUID();
      // TODO: create example payment
      Payment payment = new Payment(uuid.toString(), 100.0, "123456", null, null);
      // send payment for authorization to coreservices
      paymentAuthorizationService.sendPaymentAuthorizationRequest(payment, actx);
            
      return null;
    }

    @Override
    public void onComplete(AsyncEvent event) throws IOException {
      log.debug("===== PAYMENT [" + Long.toString(this.requestId) + "] PROCESSING IS FINISHED! =====");
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
      log.debug("Payment timeout!");
      onComplete(event);
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {
      log.debug("Payment error!");
      onComplete(event);
    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {
      log.debug("Payment [", this.requestId, "] async start!");
    }
  }
}
