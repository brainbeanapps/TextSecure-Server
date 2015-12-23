package org.whispersystems.textsecuregcm.push;

import io.dropwizard.lifecycle.Managed;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.whispersystems.textsecuregcm.configuration.PushymeConfiguration;
import org.whispersystems.textsecuregcm.entities.PushymeMessage;
import org.whispersystems.textsecuregcm.entities.UnregisteredEvent;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PushymeClient implements Managed, PushFeedbackService {

    private static final Logger LOGGER = LogManager.getLogger(PushymeClient.class);

    private final Client httpClient;
    private final ExecutorService workers;
    private final String endpoint;

    public PushymeClient(PushymeConfiguration configuration, Client httpClient) {
        this.httpClient = httpClient;
        endpoint = "https://pushy.me/push?api_key=" + configuration.getPushymeApiKey();

        workers = new ThreadPoolExecutor(configuration.getWorkerThreadsCount(), configuration.getWorkerThreadsCount(),
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(configuration.getMaxPendingTasks()),
                new WorkerThreadFactory());

        LOGGER.debug("Pushy client instantiated");
    }

    public void push(PushymeMessage message) throws TransientPushFailureException {
        try {
            workers.submit(new SendTask(message));
        } catch (Exception e) {
            throw new TransientPushFailureException(e);
        }
    }

    private void send(PushymeMessage message) {
        PushyPushRequest pushyRequest = new PushyPushRequest(message.getGcmId());
        String  key     = message.isReceipt() ? "receipt" : message.isNotification() ? "notification" :  "message";
        pushyRequest.withData(key, message.getMessage());
        Response response = httpClient.target(endpoint)
                .request()
                .header("Content-Type", "application/json")
                .post(Entity.entity(pushyRequest, MediaType.APPLICATION_JSON));
        
        if (response.getStatus() >= 400) {
            LOGGER.warn("Bad response from pushyme. " + response);
        }
    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void stop() throws Exception {
        workers.shutdownNow();
    }

    @Override
    public List<UnregisteredEvent> getFeedback(PushService pushService) throws IOException {
        return Collections.emptyList();
    }

    class SendTask implements Runnable {
        private final PushymeMessage message;

        SendTask(PushymeMessage message) {
            this.message = message;
        }

        @Override
        public void run() {
            send(message);
        }
    }
}

class WorkerThreadFactory implements ThreadFactory {

    private static AtomicInteger counter = new AtomicInteger();

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
        thread.setName("Pushy-worker-" + counter.incrementAndGet());
        return thread;
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



