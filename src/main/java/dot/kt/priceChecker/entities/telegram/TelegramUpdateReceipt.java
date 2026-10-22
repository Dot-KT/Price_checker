package dot.kt.priceChecker.entities.telegram;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "telegram_updates")
public class TelegramUpdateReceipt {
    @Id @Column(name = "update_id") private long updateId;
    @Column(name = "telegram_id", nullable = false) private long telegramId;
    @Column(name = "chat_id", nullable = false) private long chatId;
    @Column(name = "incoming_json", nullable = false, columnDefinition = "text") private String incomingJson;
    @Column(name = "replies_json", columnDefinition = "text") private String repliesJson;
    @Column(name = "delivered_count", nullable = false) private int deliveredCount;
    @Column(name = "lease_until") private Instant leaseUntil;
    @Column(name = "received_at", nullable = false, updatable = false) private Instant receivedAt;
    @Column(name = "processed_at") private Instant processedAt;
    protected TelegramUpdateReceipt() { }
    public TelegramUpdateReceipt(long updateId, long telegramId, long chatId, String incomingJson, Instant now) {
        this.updateId = updateId; this.telegramId = telegramId; this.chatId = chatId; this.incomingJson = incomingJson; this.receivedAt = now;
    }
    public void lease(Instant until) { this.leaseUntil = until; }
    public void complete(String replies, Instant now) { this.repliesJson = replies; this.processedAt = now; this.leaseUntil = null; }
    public void delivered(int count) { this.deliveredCount = count; this.leaseUntil = null; }
    public void finishDelivery() { this.repliesJson = null; this.leaseUntil = null; }
    public long getUpdateId() { return updateId; }
    public long getTelegramId() { return telegramId; }
    public long getChatId() { return chatId; }
    public String getIncomingJson() { return incomingJson; }
    public String getRepliesJson() { return repliesJson; }
    public int getDeliveredCount() { return deliveredCount; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public Instant getProcessedAt() { return processedAt; }
}
