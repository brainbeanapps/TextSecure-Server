package org.whispersystems.textsecuregcm.push;

import io.dropwizard.lifecycle.Managed;
import org.apache.log4j.Logger;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

abstract class PushClient<T>  implements Managed{

    private static final Logger LOGGER = Logger.getLogger(PushClient.class);

    protected final ExecutorService workers;

    public PushClient(int workerCount, int maxPendingTasks) {
        workers = new ThreadPoolExecutor(workerCount, workerCount,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(maxPendingTasks),
                new WorkerThreadFactory());
    }

    protected abstract T createMessage(Account account, Device device, String message, boolean isReceipt, boolean isNotification);

    protected abstract void sendMessage(T message);

    public void push(Account account, Device device, String message, boolean isReceipt, boolean isNotification) throws TransientPushFailureException {
        try {
            workers.submit(new SendTask(createMessage(account, device, message, isReceipt, isNotification)));
        } catch (Exception e) {
            throw new TransientPushFailureException(e);
        }
    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public void stop() throws Exception {
        workers.shutdownNow();
    }

    class SendTask implements Runnable {
        private final T message;

        SendTask(T message) {
            this.message = message;
        }

        @Override
        public void run() {
            sendMessage(message);
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
