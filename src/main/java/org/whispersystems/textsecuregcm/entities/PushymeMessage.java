package org.whispersystems.textsecuregcm.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;

public class PushymeMessage {

    @JsonProperty
    @NotEmpty
    private String gcmId;

    @JsonProperty
    @NotEmpty
    private String number;

    @JsonProperty
    @Min(1)
    private int deviceId;

    @JsonProperty
    private String message;

    @JsonProperty
    private boolean receipt;

    @JsonProperty
    private boolean notification;

    public PushymeMessage() {}

    public PushymeMessage(String gcmId, String number, int deviceId, String message, boolean receipt, boolean notification) {
        this.gcmId        = gcmId;
        this.number       = number;
        this.deviceId     = deviceId;
        this.message      = message;
        this.receipt      = receipt;
        this.notification = notification;
    }

    public String getNumber() {
        return number;
    }

    public String getGcmId() {
        return gcmId;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public String getMessage() {
        return message;
    }

    public boolean isReceipt() {
        return receipt;
    }

    public boolean isNotification() {
        return notification;
    }
}
