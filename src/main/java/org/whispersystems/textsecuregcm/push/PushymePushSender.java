package org.whispersystems.textsecuregcm.push;

import org.whispersystems.textsecuregcm.entities.MessageProtos.Envelope;
import org.whispersystems.textsecuregcm.entities.PushymeMessage;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;

public class PushymePushSender extends PushSender {

    private PushymeClient pushClient;

    public PushymePushSender(PushymeClient client, WebsocketSender websocketSender) {
        super(null, null, websocketSender);
        pushClient = client;
    }

    @Override
    public void sendMessage(Account account, Device device, Envelope message) throws NotPushRegisteredException, TransientPushFailureException {
        if (device.getPushymeId() != null) {
            sendPushyme(account, device, message);
        } else if (device.getFetchesMessages()) {
            sendWebSocketMessage(account, device, message);
        } else {
            throw new NotPushRegisteredException("No push destination for " + account.getNumber());
        }
    }

    private void sendPushyme(Account account, Device device, Envelope message) throws TransientPushFailureException {
        WebsocketSender.DeliveryStatus status = getWebSocketSender().sendMessage(account, device, message, WebsocketSender.Type.PUSHYME);
        if (!status.isDelivered()) {
            PushymeMessage pushMessage = new PushymeMessage(device.getPushymeId(), account.getNumber(), (int) device.getId(), "", false, true);
            pushClient.push(pushMessage);
        }
    }
}
