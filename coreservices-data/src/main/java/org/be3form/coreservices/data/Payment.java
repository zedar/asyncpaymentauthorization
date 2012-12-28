package org.be3form.coreservices.data;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


/**
 *  Deposit account payment. Could be passed to payment authorization service.
 *  Authorization result returns in authorizationCode attribute.
 */
@ToString(includeFieldNames=true)
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class Payment implements Serializable {
  // Possible authorization results
  public enum PAYAUTH_RESULT {
    AUTHORIZED,
    REJECTED
  }
  // Unique identifier of the payment
  @Getter @Setter private String id;
  // Amount to be authorized
  @Getter @Setter private double amount;
  // Authorization code - secret code
  @Getter @Setter private String authorizationCode;

  // Authorization code - secret code
  @Getter @Setter private PAYAUTH_RESULT authorizationResult;
  // Account number in NRB form - as result of account assiged with authorizationCode
  @Getter @Setter private String accountNumber;
}
