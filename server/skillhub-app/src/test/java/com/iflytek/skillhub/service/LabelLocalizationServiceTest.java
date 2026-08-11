package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.label.LabelTranslation;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

class LabelLocalizationServiceTest {

    private final LabelLocalizationService service = new LabelLocalizationService();

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void resolveDisplayNameShouldMatchLanguageOnlyLocaleToRegionalTranslation() {
        LocaleContextHolder.setLocale(Locale.CHINESE);

        String displayName = service.resolveDisplayName("data-analysis", List.of(
                new LabelTranslation(1L, "zh-CN", "数据分析"),
                new LabelTranslation(1L, "en-US", "Data Analysis")
        ));

        assertThat(displayName).isEqualTo("数据分析");
    }

    @Test
    void resolveDisplayNameShouldFallbackToEnglishRegionalTranslation() {
        LocaleContextHolder.setLocale(Locale.GERMAN);

        String displayName = service.resolveDisplayName("data-analysis", List.of(
                new LabelTranslation(1L, "en-US", "Data Analysis")
        ));

        assertThat(displayName).isEqualTo("Data Analysis");
    }
}
