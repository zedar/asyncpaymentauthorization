package org.be3form.web.servlet;

import javax.servlet.annotation.WebServlet;

import org.springframework.web.context.support.HttpRequestHandlerServlet;

/**
 *  Servlet implementation of payment authorization
 */
@WebServlet(
  description="Payment authorization http servlet", 
  urlPatterns= {"/paymentauth"}, 
  name="paymentAuthorizationHttpServletHandler",
  asyncSupported=true)
public class PaymentAuthorizationHttpServlet extends HttpRequestHandlerServlet {

}
