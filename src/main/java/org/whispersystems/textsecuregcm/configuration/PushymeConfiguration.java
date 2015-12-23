package org.whispersystems.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

public class PushymeConfiguration {

    @JsonProperty
    @NotEmpty
    private String pushymeApiKey;

    @JsonProperty
    private int maxPendingTasks = 10_000;

    @JsonProperty
    private int workerThreadsCount = 20;

    public String getPushymeApiKey() {
        return pushymeApiKey;
    }

    public int getMaxPendingTasks() {
        return maxPendingTasks;
    }

    public int getWorkerThreadsCount() {
        return workerThreadsCount;
    }
}
