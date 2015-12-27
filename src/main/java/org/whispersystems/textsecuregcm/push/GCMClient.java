package org.whispersystems.textsecuregcm.push;

import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;
import org.apache.log4j.Logger;
import org.eclipse.jetty.util.ConcurrentHashSet;
import org.whispersystems.textsecuregcm.entities.UnregisteredEvent;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class GCMClient extends PushClient<MessageWithDestination> {

    private static final Logger LOGGER = Logger.getLogger(GCMClient.class);

    private static final int SEND_RETRIES_COUNT = 3;

    private final Sender sender;

    private ConcurrentHashSet<UnregisteredEvent> unregisteredEvents = new ConcurrentHashSet<>();

    public GCMClient(String apiKey, int workerCount, int maxPendingTasks) {
        super(workerCount, maxPendingTasks);
        sender = new Sender(apiKey);
    }

    @Override
    protected MessageWithDestination createMessage(Account account, Device device, String message, boolean isReceipt, boolean isNotification) {
        String  key     = isReceipt ? "receipt" : isNotification ? "notification" :  "message";
        Message gcmMessage = new Message.Builder()
                .addData(key, message)
                .build();
        return new MessageWithDestination(gcmMessage, device.getGcmId(), account.getNumber(), (int) device.getId());
    }

    @Override
    protected void sendMessage(MessageWithDestination message) {
        try {
            LOGGER.debug("Sending gcm push to " + message.number);
            Result result = sender.send(message.message, message.destRegId, SEND_RETRIES_COUNT);
            String canonicalRegistrationId = result.getCanonicalRegistrationId();
            if (canonicalRegistrationId != null) {
                addUnregisteredEvent(message, canonicalRegistrationId);
            }
        } catch (IOException e) {
            LOGGER.warn("Can't send push to " + message.destRegId);
        }
    }

    protected void addUnregisteredEvent(MessageWithDestination message, String canonicalId) {
        UnregisteredEvent event = new UnregisteredEvent(message.destRegId, canonicalId, message.number, message.deviceId, System.currentTimeMillis());
        unregisteredEvents.add(event);
    }

    protected Set<UnregisteredEvent> getUnregisteredEvents() {
        Set<UnregisteredEvent> result = unregisteredEvents;
        unregisteredEvents = new ConcurrentHashSet<>();
        return new HashSet<>(result);
    }
}

class MessageWithDestination {
    final Message message;
    final String destRegId;
    final String number;
    final int deviceId;

    MessageWithDestination(Message message, String destRegId, String number, int deviceId) {
        this.message = message;
        this.destRegId = destRegId;
        this.number = number;
        this.deviceId = deviceId;
    }
}


