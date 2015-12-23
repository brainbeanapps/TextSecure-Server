package org.whispersystems.textsecuregcm.push;

import org.whispersystems.textsecuregcm.entities.UnregisteredEvent;

import java.io.IOException;
import java.util.List;

public interface PushFeedbackService {

    List<UnregisteredEvent> getFeedback(PushService pushService) throws IOException;

    enum PushService {
        APN, GCM, PUSHYME
    }
}
