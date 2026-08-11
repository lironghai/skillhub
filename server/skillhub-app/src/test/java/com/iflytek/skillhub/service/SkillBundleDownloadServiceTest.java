package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skillbundle.SkillBundle;
import com.iflytek.skillhub.domain.skillbundle.SkillBundleService;
import com.iflytek.skillhub.domain.skillbundle.SkillBundleStatus;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillBundleDownloadServiceTest {

    @Mock
    private SkillBundleService skillBundleService;

    @Mock
    private SkillDownloadService skillDownloadService;

    @Test
    void downloadShouldAggregateSkillsAsWorkbenchNeutralSkillDirectories() throws Exception {
        Map<Long, NamespaceRole> roles = Map.of(1L, NamespaceRole.MEMBER);
        SkillBundle bundle = publishedBundle("superpowers");
        when(skillBundleService.getVisibleDetail(eq("global"), eq("superpowers"), eq("user-1"), eq(roles)))
                .thenReturn(new SkillBundleService.BundleDetail(
                        bundle,
                        "global",
                        List.of(),
                        List.of(
                                new SkillBundleService.BundleItemDetail(10L, "global", "brainstorming", "Brainstorming", 0, null),
                                new SkillBundleService.BundleItemDetail(11L, "global", "test-driven-development", "TDD", 1, null)
                        ),
                        false
                ));
        when(skillDownloadService.downloadLatest(eq("global"), eq("brainstorming"), eq("user-1"), eq(roles)))
                .thenReturn(downloadResult(zipBytes(Map.of(
                        "SKILL.md", "Brainstorming skill",
                        "references/prompts.md", "Prompt reference",
                        "AGENTS.md", "Workbench adapter",
                        "nested/CLAUDE.md", "Workbench adapter",
                        "nested/GEMINI.md", "Workbench adapter"
                ))));
        when(skillDownloadService.downloadLatest(eq("global"), eq("test-driven-development"), eq("user-1"), eq(roles)))
                .thenReturn(downloadResult(zipBytes(Map.of(
                        "SKILL.md", "TDD skill"
                ))));

        SkillBundleDownloadService.DownloadResult result =
                new SkillBundleDownloadService(skillBundleService, skillDownloadService)
                        .downloadLatest("global", "superpowers", "user-1", roles);

        Map<String, String> entries = readZipEntries(result.openContent());
        assertEquals("superpowers.zip", result.filename());
        assertEquals("application/zip", result.contentType());
        assertTrue(result.contentLength() > 0);
        assertEquals("Brainstorming skill", entries.get("brainstorming/SKILL.md"));
        assertEquals("Prompt reference", entries.get("brainstorming/references/prompts.md"));
        assertEquals("TDD skill", entries.get("test-driven-development/SKILL.md"));
        assertFalse(entries.containsKey("AGENTS.md"));
        assertFalse(entries.containsKey("CLAUDE.md"));
        assertFalse(entries.containsKey("GEMINI.md"));
        assertFalse(entries.containsKey("brainstorming/AGENTS.md"));
        assertFalse(entries.containsKey("brainstorming/nested/CLAUDE.md"));
        assertFalse(entries.containsKey("brainstorming/nested/GEMINI.md"));
        verify(skillBundleService).recordDownload(bundle);
    }

    @Test
    void downloadShouldRejectContainedSkillZipWithoutRootSkillMarkdown() {
        SkillBundle bundle = publishedBundle("broken-pack");
        when(skillBundleService.getVisibleDetail(eq("global"), eq("broken-pack"), eq(null), eq(Map.of())))
                .thenReturn(new SkillBundleService.BundleDetail(
                        bundle,
                        "global",
                        List.of(),
                        List.of(new SkillBundleService.BundleItemDetail(10L, "global", "broken-skill", "Broken", 0, null)),
                        false
                ));
        when(skillDownloadService.downloadLatest(eq("global"), eq("broken-skill"), eq(null), eq(Map.of())))
                .thenReturn(downloadResult(zipBytes(Map.of("README.md", "Missing skill entrypoint"))));

        DomainBadRequestException exception = assertThrows(
                DomainBadRequestException.class,
                () -> new SkillBundleDownloadService(skillBundleService, skillDownloadService)
                        .downloadLatest("global", "broken-pack", null, Map.of())
        );

        assertEquals("error.skillBundle.download.invalidSkill", exception.messageCode());
        assertArrayEquals(new Object[]{"broken-skill"}, exception.messageArgs());
    }

    @Test
    void downloadShouldRejectAggregateZipExceedingConfiguredLimit() {
        SkillBundle bundle = publishedBundle("large-pack");
        when(skillBundleService.getVisibleDetail(eq("global"), eq("large-pack"), eq(null), eq(Map.of())))
                .thenReturn(new SkillBundleService.BundleDetail(
                        bundle,
                        "global",
                        List.of(),
                        List.of(new SkillBundleService.BundleItemDetail(10L, "global", "large-skill", "Large", 0, null)),
                        false
                ));
        when(skillDownloadService.downloadLatest(eq("global"), eq("large-skill"), eq(null), eq(Map.of())))
                .thenReturn(downloadResult(zipBytes(Map.of(
                        "SKILL.md", "Large skill",
                        "references/large.txt", "012345678901234567890123456789"
                ))));

        DomainBadRequestException exception = assertThrows(
                DomainBadRequestException.class,
                () -> new SkillBundleDownloadService(skillBundleService, skillDownloadService, 10, 20)
                        .downloadLatest("global", "large-pack", null, Map.of())
        );

        assertEquals("error.skillBundle.download.tooLarge", exception.messageCode());
        assertArrayEquals(new Object[]{"large-pack"}, exception.messageArgs());
    }

    private SkillBundle publishedBundle(String slug) {
        SkillBundle bundle = new SkillBundle(1L, "Superpowers", slug, "owner-1", SkillVisibility.PUBLIC);
        bundle.setStatus(SkillBundleStatus.PUBLISHED);
        return bundle;
    }

    private SkillDownloadService.DownloadResult downloadResult(byte[] content) {
        return new SkillDownloadService.DownloadResult(
                () -> new ByteArrayInputStream(content),
                "skill.zip",
                content.length,
                "application/zip",
                null,
                false
        );
    }

    private byte[] zipBytes(Map<String, String> entries) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Map<String, String> readZipEntries(InputStream inputStream) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                zipInputStream.transferTo(content);
                entries.put(entry.getName(), content.toString(StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
