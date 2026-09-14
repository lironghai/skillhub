package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.social.SkillSubscriptionService;
import com.iflytek.skillhub.domain.social.SubscriptionMetadataAccessPolicy;
import com.iflytek.skillhub.domain.social.SubscriptionRecipientEligibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuSubscriptionWebhookListenerTest {
    @Mock SkillRepository skillRepository;
    @Mock SkillVersionRepository skillVersionRepository;
    @Mock NamespaceRepository namespaceRepository;
    @Mock SkillSubscriptionService subscriptionService;
    @Mock UserAccountRepository accountRepository;
    @Mock NamespaceMemberRepository memberRepository;
    @Mock IdentityBindingRepository identityBindingRepository;
    @Mock FeishuWebhookNotificationSender sender;

    private FeishuSubscriptionWebhookListener listener;

    @BeforeEach
    void setUp() {
        listener = new FeishuSubscriptionWebhookListener(
                skillRepository, skillVersionRepository, namespaceRepository, subscriptionService,
                accountRepository, identityBindingRepository, new SubscriptionRecipientEligibility(
                        accountRepository, memberRepository, new SubscriptionMetadataAccessPolicy()), sender);
    }

    @Test
    void sendsOneBatchForAllEligibleFeishuSubscribers() {
        Skill skill = new Skill(5L, "skill-slug", "publisher", SkillVisibility.PUBLIC);
        skill.setDisplayName("Test Skill");
        skill.setLatestVersionId(10L);
        setId(skill, 1L);
        Namespace namespace = new Namespace("demo", "Demo", "publisher");
        namespace.setStatus(NamespaceStatus.ACTIVE);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(new SkillVersion(1L, "1.2.3", "publisher")));
        when(namespaceRepository.findById(5L)).thenReturn(Optional.of(namespace));
        when(subscriptionService.findSubscribersBySkillId(1L)).thenReturn(List.of("publisher", "feishu", "github"));
        when(accountRepository.findByIdIn(List.of("publisher", "feishu", "github"))).thenReturn(List.of(
                new UserAccount("publisher", "Publisher", null, null),
                new UserAccount("feishu", "Feishu", "feishu@example.com", null),
                new UserAccount("github", "GitHub", "github@example.com", null)));
        when(accountRepository.findByIdIn(List.of("feishu", "github"))).thenReturn(List.of(
                new UserAccount("feishu", "Feishu", "feishu@example.com", null),
                new UserAccount("github", "GitHub", "github@example.com", null)));
        when(memberRepository.findByNamespaceIdAndUserIdIn(5L, List.of("publisher", "feishu", "github")))
                .thenReturn(List.of());
        IdentityBinding binding = new IdentityBinding("feishu", "feishu", "tenant:on_union", "Feishu");
        when(identityBindingRepository.findByUserIdInAndProviderCode(
                List.of("feishu", "github"), "feishu")).thenReturn(List.of(binding));

        listener.onSkillPublished(new SkillPublishedEvent(1L, 10L, "publisher"));

        ArgumentCaptor<FeishuWebhookNotificationPayload> captor = ArgumentCaptor.forClass(FeishuWebhookNotificationPayload.class);
        verify(sender).send(captor.capture());
        FeishuWebhookNotificationPayload payload = captor.getValue();
        assertThat(payload.skillSlug()).isEqualTo("demo/skill-slug");
        assertThat(payload.skillVersion()).isEqualTo("1.2.3");
        assertThat(payload.recipients()).containsExactly(
                new FeishuWebhookNotificationPayload.Recipient("tenant:on_union", "feishu@example.com"));
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
