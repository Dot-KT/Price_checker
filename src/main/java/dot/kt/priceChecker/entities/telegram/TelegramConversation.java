package dot.kt.priceChecker.entities.telegram;

import dot.kt.priceChecker.enums.catalog.GadgetType;
import dot.kt.priceChecker.enums.catalog.ItemDomain;
import dot.kt.priceChecker.enums.telegram.ConversationPhase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telegram_conversations")
public class TelegramConversation {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private TelegramUser user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ConversationPhase phase;
    @Enumerated(EnumType.STRING) @Column(length = 32) private ItemDomain domain;
    @Enumerated(EnumType.STRING) @Column(name = "gadget_type", length = 32) private GadgetType gadgetType;
    @Column(name = "question_text", length = 4096) private String question;
    @Column(name = "intent_json", columnDefinition = "text") private String intentJson;
    @Column(name = "item_ids_json", nullable = false, columnDefinition = "text") private String itemIdsJson = "[]";
    @Column(name = "candidate_ids_json", nullable = false, columnDefinition = "text") private String candidateIdsJson = "[]";
    @Column(name = "offer_ids_json", nullable = false, columnDefinition = "text") private String offerIdsJson = "[]";
    @Column(name = "offer_snapshots_json", nullable = false, columnDefinition = "text") private String offerSnapshotsJson = "[]";
    @Column(name = "keyboard_version", nullable = false) private int keyboardVersion;
    @Column(name = "selection_json", columnDefinition = "text") private String selectionJson;
    @Column(name = "pending_update_id") private Long pendingUpdateId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "last_activity_at", nullable = false) private Instant lastActivityAt;
    @Column(name = "ended_at") private Instant endedAt;
    @Version @Column(nullable = false) private long revision;

    protected TelegramConversation() { }
    public TelegramConversation(TelegramUser user, Instant now) {
        this.id = UUID.randomUUID(); this.user = user; this.phase = ConversationPhase.NAME;
        this.createdAt = now; this.lastActivityAt = now;
    }
    public void touch(Instant now) { this.lastActivityAt = now; }
    public void phase(ConversationPhase phase) { this.phase = phase; }
    public void chooseDomain(ItemDomain domain) { this.domain = domain; this.gadgetType = null; this.phase = domain == ItemDomain.GADGET ? ConversationPhase.GADGET_TYPE : ConversationPhase.REQUEST; }
    public void chooseGadgetType(GadgetType type) { this.gadgetType = type; this.phase = ConversationPhase.REQUEST; }
    public void question(String question, long updateId) { this.question = question; this.pendingUpdateId = updateId; }
    public void answered() { this.pendingUpdateId = null; }
    public void context(String intentJson, String itemIdsJson, String candidateIdsJson) {
        this.intentJson = intentJson; this.itemIdsJson = itemIdsJson; this.candidateIdsJson = candidateIdsJson;
    }
    public void offerIds(String json) { this.offerIdsJson = json; }
    public void offerSnapshots(String json) { this.offerSnapshotsJson = json; }
    public void nextButtons() { this.keyboardVersion = Math.incrementExact(this.keyboardVersion); }
    public int getKeyboardVersion() { return keyboardVersion; }
    public String getOfferSnapshotsJson() { return offerSnapshotsJson; }
    public void select(String snapshot) { this.selectionJson = snapshot; }
    public void end(ConversationPhase reason, Instant now) { this.phase = reason; this.endedAt = now; this.pendingUpdateId = null; }
    public UUID getId() { return id; }
    public TelegramUser getUser() { return user; }
    public ConversationPhase getPhase() { return phase; }
    public ItemDomain getDomain() { return domain; }
    public GadgetType getGadgetType() { return gadgetType; }
    public String getQuestion() { return question; }
    public String getIntentJson() { return intentJson; }
    public String getItemIdsJson() { return itemIdsJson; }
    public String getCandidateIdsJson() { return candidateIdsJson; }
    public String getOfferIdsJson() { return offerIdsJson; }
    public String getSelectionJson() { return selectionJson; }
    public Long getPendingUpdateId() { return pendingUpdateId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public Instant getEndedAt() { return endedAt; }
}
