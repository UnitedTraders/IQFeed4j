package net.jacobpeterson.iqfeed4j.feed.streaming.level1;

import net.jacobpeterson.iqfeed4j.feed.message.FeedMessageListener;
import net.jacobpeterson.iqfeed4j.feed.message.SingleMessageFuture;
import net.jacobpeterson.iqfeed4j.model.feed.streaming.level1.FundamentalData;
import net.jacobpeterson.iqfeed4j.model.feed.streaming.level1.SummaryUpdate;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Level1FeedTest {

    private static <T> FeedMessageListener<T> throwingListener() {
        return new FeedMessageListener<>() {
            @Override
            public void onMessageReceived(T message) {}

            @Override
            public void onMessageException(Exception exception) {
                throw new IllegalStateException("Listener failure");
            }
        };
    }

    private static <T> FeedMessageListener<T> countingListener(AtomicInteger counter) {
        return new FeedMessageListener<>() {
            @Override
            public void onMessageReceived(T message) {}

            @Override
            public void onMessageException(Exception exception) {
                counter.incrementAndGet();
            }
        };
    }

    @Test
    void onFeedSocketExceptionNotifiesAllListenersAndCompletesFuturesEvenIfAListenerThrows() {
        Level1Feed feed = new Level1Feed("test", "localhost", 5009);
        RuntimeException socketException = new RuntimeException("socket down");
        AtomicInteger notifiedCount = new AtomicInteger();

        // The throwing listener is added first so that the pre-fix implementation would abort on it
        feed.fundamentalDataListenersOfSymbols.put("THROWING", throwingListener());
        feed.summaryUpdateListenersOfSymbols.put("COUNTING", countingListener(notifiedCount));
        SingleMessageFuture<LocalDateTime> timestampFuture = new SingleMessageFuture<>();
        feed.timestampFuturesQueue.add(timestampFuture);

        assertDoesNotThrow(() -> feed.onFeedSocketException(socketException));

        assertEquals(1, notifiedCount.get());
        assertTrue(timestampFuture.isCompletedExceptionally());
    }

    @Test
    void onFeedSocketExceptionToleratesReentrantMutationsFromFutureCallbacks() {
        Level1Feed feed = new Level1Feed("test", "localhost", 5009);
        SingleMessageFuture<LocalDateTime> reentrantFuture = new SingleMessageFuture<>();
        reentrantFuture.whenComplete((result, throwable) -> {
            feed.summaryUpdateListenersOfSymbols.put("REENTRANT", countingListener(new AtomicInteger()));
            feed.timestampFuturesQueue.add(new SingleMessageFuture<>());
        });
        feed.timestampFuturesQueue.add(reentrantFuture);

        assertDoesNotThrow(() -> feed.onFeedSocketException(new RuntimeException("socket down")));
    }

    @Test
    void onFeedSocketExceptionDoesNotThrowConcurrentModificationException() throws Exception {
        Level1Feed feed = new Level1Feed("test", "localhost", 5009);
        FeedMessageListener<SummaryUpdate> listener = countingListener(new AtomicInteger());
        for (int i = 0; i < 100; i++) {
            feed.summaryUpdateListenersOfSymbols.put("SYMBOL_" + i, listener);
        }

        int iterations = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Simulates concurrent watch/unwatch requests which mutate the listener collections while
            // holding 'messageReceivedLock', as the pre-fix 'onFeedSocketException' did not take that lock.
            Future<?> mutatorFuture = executor.submit(() -> {
                for (int i = 0; i < iterations; i++) {
                    synchronized (feed.messageReceivedLock) {
                        feed.summaryUpdateListenersOfSymbols.put("MUTATED_" + i, listener);
                        feed.summaryUpdateListenersOfSymbols.remove("MUTATED_" + i);
                    }
                }
            });
            Future<?> exceptionCallerFuture = executor.submit(() -> {
                for (int i = 0; i < iterations; i++) {
                    feed.onFeedSocketException(new RuntimeException("socket down " + i));
                }
            });

            exceptionCallerFuture.get(60, TimeUnit.SECONDS);
            mutatorFuture.get(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
}
