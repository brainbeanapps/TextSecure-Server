package org.whispersystems.textsecuregcm.push;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.whispersystems.textsecuregcm.configuration.EmbeddedPushConfiguration;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

public class PushymeClient extends PushClient<PushyPushRequest> {

    private static final Logger LOGGER = LogManager.getLogger(PushymeClient.class);

    protected final String endpoint;
    private final Client httpClient;

    public PushymeClient(EmbeddedPushConfiguration configuration, Client httpClient) {
        super(configuration.getWorkerThreadsCount(), configuration.getMaxPendingTasks());
        this.httpClient = httpClient;
        endpoint = "https://pushy.me/push?api_key=" + configuration.getPushymeApiKey();

        LOGGER.debug("Pushy client instantiated");
    }

    @Override
    protected PushyPushRequest createMessage(Account account, Device device, String message, boolean isReceipt, boolean isNotification) {
        PushyPushRequest pushyRequest = new PushyPushRequest(device.getPushymeId());
        String  key     = isReceipt ? "receipt" : isNotification ? "notification" :  "message";
        pushyRequest.withData(key, message);

        return pushyRequest;
    }

    @Override
    protected void sendMessage(PushyPushRequest pushyRequest) {
        Response response = httpClient.target(endpoint)
                .request()
                .header("Content-Type", "application/json")
                .post(Entity.entity(pushyRequest, MediaType.APPLICATION_JSON));

        if (response.getStatus() >= 400) {
            LOGGER.warn("Bad response from pushyme. " + response);
        }
    }
}

class PushyPushRequest {
    public Map<String, String> data = new HashMap<>();
    public String[] registration_ids;

    public PushyPushRequest(String regId) {
        this(new String[]{regId});
    }

    public PushyPushRequest(String[] registrationIDs) {
        this.registration_ids = registrationIDs;
    }

    void withData(String key, String data) {
        this.data.put(key, data);
    }
}



