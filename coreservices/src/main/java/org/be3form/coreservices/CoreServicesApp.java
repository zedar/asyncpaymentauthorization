package org.be3form.coreservices;

import java.io.IOException;

import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import org.springframework.integration.MessageChannel;
import org.springframework.integration.Message;
import org.springframework.integration.support.MessageBuilder;

import org.be3form.coreservices.data.Payment;

/**
 *  Starting place for all banking services
 */
public class CoreServicesApp {
  public static void main (String[] args) throws InterruptedException, IOException {
    System.out.println("===== STARTED =====");
    System.out.println("Enter 'c' in order to stop APP");

    AbstractApplicationContext context = new ClassPathXmlApplicationContext("/META-INF/org/be3form/coreservices/amq-config.xml");

    // hang and wait for user action
    try {
      char c = 0;
      while (c != 'c') {
        c = (char)System.in.read();
      }
    }
    catch (Exception ex) {
      // Ignore terminations
    }
    finally {
      System.out.println("===== FINISHED =====");
      context.close();
    }
  }
}
