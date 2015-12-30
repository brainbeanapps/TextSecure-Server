/**
 * Copyright (C) 2013 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.controllers;

import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import io.dropwizard.auth.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.auth.*;
import org.whispersystems.textsecuregcm.entities.AccountAttributes;
import org.whispersystems.textsecuregcm.entities.ApnRegistrationId;
import org.whispersystems.textsecuregcm.entities.GcmRegistrationId;
import org.whispersystems.textsecuregcm.entities.PushymeRegistrationId;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.providers.TimeProvider;
import org.whispersystems.textsecuregcm.sms.SmsSender;
import org.whispersystems.textsecuregcm.sms.TwilioSmsSender;
import org.whispersystems.textsecuregcm.storage.*;
import org.whispersystems.textsecuregcm.util.Util;
import org.whispersystems.textsecuregcm.util.VerificationCode;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;

@Path("/v1/accounts")
public class AccountController {

  private final Logger logger = LoggerFactory.getLogger(AccountController.class);

  private final PendingAccountsManager                pendingAccounts;
  private final AccountsManager                       accounts;
  private final RateLimiters                          rateLimiters;
  private final SmsSender                             smsSender;
  private final MessagesManager                       messagesManager;
  private final TimeProvider                          timeProvider;
  private final Optional<AuthorizationTokenGenerator> tokenGenerator;
  private final Map<String, Integer>                  testDevices;

  public AccountController(PendingAccountsManager pendingAccounts,
                           AccountsManager accounts,
                           RateLimiters rateLimiters,
                           SmsSender smsSenderFactory,
                           MessagesManager messagesManager,
                           TimeProvider timeProvider,
                           Optional<byte[]> authorizationKey,
                           Map<String, Integer> testDevices)
  {
    this.pendingAccounts  = pendingAccounts;
    this.accounts         = accounts;
    this.rateLimiters     = rateLimiters;
    this.smsSender        = smsSenderFactory;
    this.messagesManager  = messagesManager;
    this.timeProvider     = timeProvider;
    this.testDevices      = testDevices;

    if (authorizationKey.isPresent()) {
      tokenGenerator = Optional.of(new AuthorizationTokenGenerator(authorizationKey.get()));
    } else {
      tokenGenerator = Optional.absent();
    }
  }

  @Timed
  @GET
  @Path("/{transport}/code/{number}")
  public Response createAccount(@PathParam("transport") String transport,
                                @PathParam("number")    String number,
                                @QueryParam("client")   Optional<String> client)
      throws IOException, RateLimitExceededException
  {
    if (!Util.isValidNumber(number)) {
      logger.debug("Invalid number: " + number);
      throw new WebApplicationException(Response.status(400).build());
    }

    switch (transport) {
      case "sms":
        rateLimiters.getSmsDestinationLimiter().validate(number);
        break;
      case "voice":
        rateLimiters.getVoiceDestinationLimiter().validate(number);
        break;
      default:
        throw new WebApplicationException(Response.status(422).build());
    }

    VerificationCode verificationCode = generateVerificationCode(number);
    pendingAccounts.store(number, verificationCode.getVerificationCode());

    if (testDevices.containsKey(number)) {
      // noop
    } else if (transport.equals("sms")) {
      smsSender.deliverSmsVerification(number, client, verificationCode.getVerificationCodeDisplay());
    } else if (transport.equals("voice")) {
      smsSender.deliverVoxVerification(number, verificationCode.getVerificationCodeSpeech());
    }

    return Response.ok().build();
  }

  @Timed
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/code/{verification_code}")
  public void verifyAccount(@PathParam("verification_code") String verificationCode,
                            @HeaderParam("Authorization")   String authorizationHeader,
                            @HeaderParam("X-Signal-Agent")  String userAgent,
                            @Valid                          AccountAttributes accountAttributes)
      throws RateLimitExceededException
  {
    try {
      AuthorizationHeader header = AuthorizationHeader.fromFullHeader(authorizationHeader);
      String number              = header.getNumber();
      String password            = header.getPassword();

      rateLimiters.getVerifyLimiter().validate(number);

      Optional<String> storedVerificationCode = pendingAccounts.getCodeForNumber(number);

      if (!storedVerificationCode.isPresent() ||
          !verificationCode.equals(storedVerificationCode.get()))
      {
        throw new WebApplicationException(Response.status(403).build());
      }

      if (accounts.isRelayListed(number)) {
        throw new WebApplicationException(Response.status(417).build());
      }

      createAccount(number, password, userAgent, accountAttributes);
    } catch (InvalidAuthorizationHeaderException e) {
      logger.info("Bad Authorization Header", e);
      throw new WebApplicationException(Response.status(401).build());
    }
  }

  @Timed
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/token/{verification_token}")
  public void verifyToken(@PathParam("verification_token") String verificationToken,
                          @HeaderParam("Authorization")    String authorizationHeader,
                          @HeaderParam("X-Signal-Agent")   String userAgent,
                          @Valid                           AccountAttributes accountAttributes)
      throws RateLimitExceededException
  {
    try {
      AuthorizationHeader header   = AuthorizationHeader.fromFullHeader(authorizationHeader);
      String              number   = header.getNumber();
      String              password = header.getPassword();

      rateLimiters.getVerifyLimiter().validate(number);

      if (!tokenGenerator.isPresent()) {
        logger.debug("Attempt to authorize with key but not configured...");
        throw new WebApplicationException(Response.status(403).build());
      }

      if (!tokenGenerator.get().isValid(verificationToken, number, timeProvider.getCurrentTimeMillis())) {
        throw new WebApplicationException(Response.status(403).build());
      }

      createAccount(number, password, userAgent, accountAttributes);
    } catch (InvalidAuthorizationHeaderException e) {
      logger.info("Bad authorization header", e);
      throw new WebApplicationException(Response.status(401).build());
    }
  }

  @Timed
  @GET
  @Path("/token/")
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizationToken verifyToken(@Auth Account account)
      throws RateLimitExceededException
  {
    if (!tokenGenerator.isPresent()) {
      logger.debug("Attempt to authorize with key but not configured...");
      throw new WebApplicationException(Response.status(404).build());
    }

    return tokenGenerator.get().generateFor(account.getNumber());
  }

  @Timed
  @PUT
  @Path("/gcm/")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setGcmRegistrationId(@Auth Account account, @Valid GcmRegistrationId registrationId) {
    setPushId(account, null, null, null, registrationId.getGcmRegistrationId(), registrationId.isWebSocketChannel());
  }

  @Timed
  @DELETE
  @Path("/gcm/")
  public void deleteGcmRegistrationId(@Auth Account account) {
    setPushId(account, null, null, null, null, false);
  }

  @Timed
  @PUT
  @Path("/pushyme/")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setPushymeRegistrationId(@Auth Account account, @Valid PushymeRegistrationId registrationId) {
    setPushId(account, null, null, null, registrationId.getPushymeRegistrationId(), registrationId.isWebSocketChannel());
  }

  @Timed
  @DELETE
  @Path("/pushyme/")
  public void deletePushymeRegistrationId(@Auth Account account) {
    setPushId(account, null, null, null, null, false);
  }

  @Timed
  @PUT
  @Path("/apn/")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setApnRegistrationId(@Auth Account account, @Valid ApnRegistrationId registrationId) {
    setPushId(account, null, registrationId.getApnRegistrationId(), registrationId.getVoipRegistrationId(), null, true);
  }

  @Timed
  @DELETE
  @Path("/apn/")
  public void deleteApnRegistrationId(@Auth Account account) {
    setPushId(account, null, null, null, null, false);
  }

  private void setPushId(Account account, String gcmId, String apnId, String voipApnId, String pushymeId, boolean fetchesMessage) {
    Device device = account.getAuthenticatedDevice().get();
    device.setGcmId(gcmId);
    device.setApnId(apnId);
    device.setVoipApnId(voipApnId);
    device.setPushymeId(pushymeId);
    device.setFetchesMessages(fetchesMessage);
    accounts.update(account);
  }

  @Timed
  @PUT
  @Path("/attributes/")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setAccountAttributes(@Auth Account account,
                                   @HeaderParam("X-Signal-Agent") String userAgent,
                                   @Valid AccountAttributes attributes)
  {
    Device device = account.getAuthenticatedDevice().get();

    device.setFetchesMessages(attributes.getFetchesMessages());
    device.setName(attributes.getName());
    device.setLastSeen(Util.todayInMillis());
    device.setVoiceSupported(attributes.getVoice());
    device.setRegistrationId(attributes.getRegistrationId());
    device.setSignalingKey(attributes.getSignalingKey());
    device.setUserAgent(userAgent);

    accounts.update(account);
  }

  @Timed
  @POST
  @Path("/voice/twiml/{code}")
  @Produces(MediaType.APPLICATION_XML)
  public Response getTwiml(@PathParam("code") String encodedVerificationText) {
    return Response.ok().entity(String.format(TwilioSmsSender.SAY_TWIML,
        encodedVerificationText)).build();
  }

  private void createAccount(String number, String password, String userAgent, AccountAttributes accountAttributes) {
    Device device = new Device();
    device.setId(Device.MASTER_ID);
    device.setAuthenticationCredentials(new AuthenticationCredentials(password));
    device.setSignalingKey(accountAttributes.getSignalingKey());
    device.setFetchesMessages(accountAttributes.getFetchesMessages());
    device.setRegistrationId(accountAttributes.getRegistrationId());
    device.setName(accountAttributes.getName());
    device.setVoiceSupported(accountAttributes.getVoice());
    device.setCreated(System.currentTimeMillis());
    device.setLastSeen(Util.todayInMillis());
    device.setUserAgent(userAgent);

    Account account = new Account();
    account.setNumber(number);
    account.addDevice(device);

    accounts.create(account);
    messagesManager.clear(number);
    pendingAccounts.remove(number);
  }

  @VisibleForTesting protected VerificationCode generateVerificationCode(String number) {
    try {
      if (testDevices.containsKey(number)) {
        return new VerificationCode(testDevices.get(number));
      }

      SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
      int randomInt       = 100000 + random.nextInt(900000);
      return new VerificationCode(randomInt);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }
}
