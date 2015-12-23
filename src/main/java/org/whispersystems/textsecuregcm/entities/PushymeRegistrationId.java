package org.whispersystems.textsecuregcm.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

public class PushymeRegistrationId  {

    @JsonProperty
    @NotEmpty
    private String pushymeRegistrationId;

    @JsonProperty
    private boolean webSocketChannel;

    public String getPushymeRegistrationId() {
        return pushymeRegistrationId;
    }

    public boolean isWebSocketChannel() {
        return webSocketChannel;
    }
}
