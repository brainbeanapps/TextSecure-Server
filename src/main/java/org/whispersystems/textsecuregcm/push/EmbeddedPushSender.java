package org.whispersystems.textsecuregcm.push;

import org.whispersystems.textsecuregcm.entities.MessageProtos.Envelope;
import org.whispersystems.textsecuregcm.entities.UnregisteredEvent;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EmbeddedPushSender extends PushSender implements PushFeedbackService {

    private PushymeClient pushymeClient;
    private GCMClient gcmClient;

    public EmbeddedPushSender(PushymeClient pushymeClient, GCMClient gcmClient, WebsocketSender websocketSender) {
        super(null, null, websocketSender);
        this.pushymeClient = pushymeClient;
        this.gcmClient = gcmClient;
    }

    @Override
    public void sendMessage(Account account, Device device, Envelope message) throws NotPushRegisteredException, TransientPushFailureException {
        WebsocketSender.Type type = typeForDevice(device);
        if (type != null) {
            sendPush(account, device, message, type);
        } else if (device.getFetchesMessages()) {
            sendWebSocketMessage(account, device, message);
        } else {
            throw new NotPushRegisteredException("No push destination for " + account.getNumber());
        }
    }

    private void sendPush(Account account, Device device, Envelope message, WebsocketSender.Type type) throws TransientPushFailureException {
        WebsocketSender.DeliveryStatus status = getWebSocketSender().sendMessage(account, device, message, type);
        if (!status.isDelivered()) {
            pushClientForType(type).push(account, device, "", false, true);
        }
    }

    private WebsocketSender.Type typeForDevice(Device device) {
        if (device.getPushymeId() != null) {
            return WebsocketSender.Type.PUSHYME;
        }
        if (device.getGcmId() != null) {
            return WebsocketSender.Type.GCM;
        }
        return null;
    }

    private PushClient pushClientForType(WebsocketSender.Type type) {
        switch (type) {
            case PUSHYME:
                return pushymeClient;
            case GCM:
                return gcmClient;
            default:
                throw new IllegalArgumentException("No push sender for " + type);
        }
    }

    @Override
    public List<UnregisteredEvent> getFeedback(PushService pushService) throws IOException {
        if (pushService == PushService.GCM) {
            return new ArrayList<>(gcmClient.getUnregisteredEvents());
        }
        return Collections.emptyList();
    }
}
