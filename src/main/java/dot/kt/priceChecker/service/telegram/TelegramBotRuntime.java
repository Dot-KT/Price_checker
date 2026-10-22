package dot.kt.priceChecker.service.telegram;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.*;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.telegram.enabled", havingValue = "true")
public class TelegramBotRuntime {
    private static final Logger log = LoggerFactory.getLogger(TelegramBotRuntime.class);
    private final TelegramApiClient api;
    private final TelegramConversationStore store;
    private final TelegramConversationService service;
    private final Set<Long> busyChats = ConcurrentHashMap.newKeySet();
    private final ExecutorService workers = Executors.newFixedThreadPool(4, Thread.ofPlatform().daemon().name("telegram-worker-", 0).factory());
    private volatile boolean ready;
    private volatile Instant pollAfter = Instant.EPOCH;
    private volatile Instant deliveryAfter = Instant.EPOCH;
    public TelegramBotRuntime(TelegramApiClient api, TelegramConversationStore store, TelegramConversationService service) {
        this.api = api; this.store = store; this.service = service;
    }
    @Scheduled(initialDelay = 2000, fixedDelay = 1000)
    public void poll() {
        if (Instant.now().isBefore(pollAfter)) return;
        try {
            if (!ready) {
                var identity = api.identity();
                if (api.hasWebhook()) { log.warn("Telegram polling cannot start while a webhook is configured. Remove it before enabling this poller."); pollAfter = Instant.now().plusSeconds(60); return; }
                ready = true; log.info("Ch3cker Telegram polling started for @{}", identity.username());
            }
            for (var incoming : api.poll(store.offset())) { store.receive(incoming); api.acknowledge(incoming.callbackQueryId()); }
        } catch (TelegramApiClient.TelegramFailure failure) {
            pollAfter = Instant.now().plusSeconds(failure.retrySeconds());
            log.warn("Telegram polling temporarily unavailable (status {}). Retrying later.", failure.status());
        } catch (RuntimeException failure) {
            pollAfter = Instant.now().plusSeconds(30); log.warn("Telegram updates could not be saved; polling will retry.");
        }
    }
    @Scheduled(initialDelay = 3000, fixedDelay = 1000)
    public void dispatch() {
        if (Instant.now().isBefore(deliveryAfter) || busyChats.size() >= 4) return;
        for (var pending : store.pending()) {
            if (busyChats.size() >= 4) break;
            if (!busyChats.add(pending.chatId())) continue;
            workers.submit(() -> {
                try {
                    if (!store.claim(pending.updateId())) return;
                    service.process(pending.updateId());
                    var delivery = store.delivery(pending.updateId());
                    for (int index = delivery.delivered(); index < delivery.replies().size(); index++) {
                        api.send(delivery.chatId(), delivery.replies().get(index));
                        store.delivered(pending.updateId(), index + 1, index + 1 == delivery.replies().size());
                    }
                    if (delivery.replies().isEmpty()) store.delivered(pending.updateId(), 0, true);
                } catch (TelegramApiClient.TelegramFailure failure) {
                    if (failure.status() == 403 || failure.status() == 400) store.delivered(pending.updateId(), 0, true);
                    else {
                        store.retryLater(pending.updateId(), failure.retrySeconds());
                        if (failure.status() == 429) deliveryAfter = Instant.now().plusSeconds(failure.retrySeconds());
                    }
                    log.warn("Telegram reply delivery failed (status {}).", failure.status());
                } catch (RuntimeException failure) {
                    store.retryLater(pending.updateId(), 30); log.warn("Telegram update processing failed; it will be retried.");
                } finally { busyChats.remove(pending.chatId()); }
            });
        }
    }
    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void expire() { store.expire(); }
    @PreDestroy
    public void stop() { workers.shutdownNow(); }
}
