package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.event.SkillVersionYankedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.social.SkillSubscriptionService;
import com.iflytek.skillhub.domain.social.SubscriptionRecipientEligibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FeishuSubscriptionWebhookListener {
    private static final String FEISHU = "feishu";
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillSubscriptionService subscriptionService;
    private final UserAccountRepository accountRepository;
    private final IdentityBindingRepository identityBindingRepository;
    private final SubscriptionRecipientEligibility eligibility;
    private final FeishuWebhookNotificationSender sender;

    public FeishuSubscriptionWebhookListener(SkillRepository skillRepository,
                                              SkillVersionRepository skillVersionRepository,
                                              NamespaceRepository namespaceRepository,
                                              SkillSubscriptionService subscriptionService,
                                              UserAccountRepository accountRepository,
                                              IdentityBindingRepository identityBindingRepository,
                                              SubscriptionRecipientEligibility eligibility,
                                              FeishuWebhookNotificationSender sender) {
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.namespaceRepository = namespaceRepository;
        this.subscriptionService = subscriptionService;
        this.accountRepository = accountRepository;
        this.identityBindingRepository = identityBindingRepository;
        this.eligibility = eligibility;
        this.sender = sender;
    }

    @Async("feishuWebhookExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSkillPublished(SkillPublishedEvent event) {
        notifySubscribers(event.skillId(), event.versionId(), event.publisherId(), false, true);
    }

    @Async("feishuWebhookExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSkillVersionYanked(SkillVersionYankedEvent event) {
        notifySubscribers(event.skillId(), event.versionId(), event.actorUserId(), true, event.wasPublished());
    }

    private void notifySubscribers(Long skillId, Long versionId, String actorId,
                                   boolean yanked, boolean wasPublished) {
        skillRepository.findById(skillId).ifPresent(skill -> {
            List<String> candidates = subscriptionService.findSubscribersBySkillId(skillId);
            if (candidates.isEmpty()) return;
            Namespace namespace = namespaceRepository.findById(skill.getNamespaceId()).orElse(null);
            List<String> eligibleIds = yanked
                    ? eligibility.yankedRecipients(skill, namespace, candidates, wasPublished)
                    : eligibility.currentRecipients(skill, namespace, candidates);
            eligibleIds = eligibleIds.stream().filter(id -> !Objects.equals(id, actorId)).toList();
            if (eligibleIds.isEmpty()) return;

            Map<String, UserAccount> accounts = new LinkedHashMap<>();
            accountRepository.findByIdIn(eligibleIds).forEach(account -> accounts.put(account.getId(), account));
            Map<String, IdentityBinding> bindings = new LinkedHashMap<>();
            identityBindingRepository.findByUserIdInAndProviderCode(eligibleIds, FEISHU)
                    .forEach(binding -> bindings.putIfAbsent(binding.getUserId(), binding));
            List<FeishuWebhookNotificationPayload.Recipient> recipients = eligibleIds.stream()
                    .map(id -> recipient(bindings.get(id), accounts.get(id)))
                    .filter(Objects::nonNull)
                    .toList();
            if (recipients.isEmpty()) return;

            String name = skillName(skill);
            String version = skillVersion(versionId);
            String action = yanked ? "版本已撤回" : "发布了新版本";
            String slug = (namespace == null ? skill.getNamespaceId() : namespace.getSlug()) + "/" + skill.getSlug();
            String namespaceName = namespace == null || namespace.getDisplayName() == null
                    || namespace.getDisplayName().isBlank() ? String.valueOf(skill.getNamespaceId())
                    : namespace.getDisplayName();
            String text = "空间「" + namespaceName + "」中的技能[" + name
                    + "](https://skmcp-bdc.yingxiong.com/space/" + slug + ")"
                    + action + (version == null ? "" : "，版本号：" + version);
            sender.send(new FeishuWebhookNotificationPayload(
                    yanked ? "SUBSCRIPTION_VERSION_YANKED" : "SUBSCRIPTION_NEW_VERSION",
                    slug, version, text, recipients));
        });
    }

    private FeishuWebhookNotificationPayload.Recipient recipient(IdentityBinding binding, UserAccount account) {
        if (binding == null) return null;
        return new FeishuWebhookNotificationPayload.Recipient(binding.getSubject(), account == null ? null : account.getEmail());
    }

    private String skillName(Skill skill) {
        return skill.getDisplayName() == null || skill.getDisplayName().isBlank()
                ? skill.getSlug() : skill.getDisplayName();
    }

    private String skillVersion(Long versionId) {
        return versionId == null ? null : skillVersionRepository.findById(versionId).map(SkillVersion::getVersion).orElse(null);
    }
}
