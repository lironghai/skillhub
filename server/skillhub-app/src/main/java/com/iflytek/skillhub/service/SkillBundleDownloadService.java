package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import com.iflytek.skillhub.domain.skillbundle.SkillBundleService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SkillBundleDownloadService {

    private static final String CONTENT_TYPE = "application/zip";
    static final int DEFAULT_MAX_SKILL_COUNT = SkillBundleService.MAX_BUNDLE_ITEM_COUNT;
    static final long DEFAULT_MAX_CONTENT_BYTES = SkillPackagePolicy.MAX_TOTAL_PACKAGE_SIZE;

    private final SkillBundleService skillBundleService;
    private final SkillDownloadService skillDownloadService;
    private final int maxSkillCount;
    private final long maxContentBytes;

    @Autowired
    public SkillBundleDownloadService(SkillBundleService skillBundleService,
                                      SkillDownloadService skillDownloadService) {
        this(skillBundleService, skillDownloadService, DEFAULT_MAX_SKILL_COUNT, DEFAULT_MAX_CONTENT_BYTES);
    }

    SkillBundleDownloadService(SkillBundleService skillBundleService,
                               SkillDownloadService skillDownloadService,
                               int maxSkillCount,
                               long maxContentBytes) {
        this.skillBundleService = skillBundleService;
        this.skillDownloadService = skillDownloadService;
        this.maxSkillCount = maxSkillCount;
        this.maxContentBytes = maxContentBytes;
    }

    public record DownloadResult(
            Supplier<InputStream> contentSupplier,
            String filename,
            long contentLength,
            String contentType
    ) {
        public InputStream openContent() {
            return contentSupplier.get();
        }
    }

    public DownloadResult downloadLatest(String namespaceSlug,
                                         String bundleSlug,
                                         String currentUserId,
                                         Map<Long, NamespaceRole> userNsRoles) {
        Map<Long, NamespaceRole> safeRoles = userNsRoles != null ? userNsRoles : Map.of();
        SkillBundleService.BundleDetail detail = skillBundleService.getVisibleDetail(
                namespaceSlug,
                bundleSlug,
                currentUserId,
                safeRoles
        );
        if (detail.items().isEmpty()) {
            throw new DomainBadRequestException("error.skillBundle.download.empty", bundleSlug);
        }
        if (detail.items().size() > maxSkillCount) {
            throw new DomainBadRequestException("error.skillBundle.download.tooManySkills", bundleSlug);
        }

        byte[] bundle = buildWorkbenchNeutralZip(detail, currentUserId, safeRoles);
        skillBundleService.recordDownload(detail.bundle());
        return new DownloadResult(
                () -> new ByteArrayInputStream(bundle),
                sanitizeFilename(detail.bundle().getSlug()) + ".zip",
                bundle.length,
                CONTENT_TYPE
        );
    }

    private byte[] buildWorkbenchNeutralZip(SkillBundleService.BundleDetail detail,
                                            String currentUserId,
                                            Map<Long, NamespaceRole> userNsRoles) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            Set<String> writtenEntries = new HashSet<>();
            long[] copiedContentBytes = new long[]{0L};
            for (SkillBundleService.BundleItemDetail item : detail.items()) {
                appendSkill(zipOutputStream, writtenEntries, copiedContentBytes, detail.bundle().getSlug(), item, currentUserId, userNsRoles);
            }
            zipOutputStream.finish();
            return outputStream.toByteArray();
        } catch (DomainBadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build skill bundle download zip", ex);
        }
    }

    private void appendSkill(ZipOutputStream zipOutputStream,
                             Set<String> writtenEntries,
                             long[] copiedContentBytes,
                             String bundleSlug,
                             SkillBundleService.BundleItemDetail item,
                             String currentUserId,
                             Map<Long, NamespaceRole> userNsRoles) throws Exception {
        SkillDownloadService.DownloadResult skillDownload = skillDownloadService.downloadLatest(
                item.namespaceSlug(),
                item.skillSlug(),
                currentUserId,
                userNsRoles
        );
        String skillDirectory = sanitizePathSegment(item.skillSlug());
        boolean hasSkillMarkdown = false;
        try (ZipInputStream zipInputStream = new ZipInputStream(skillDownload.openContent())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String normalizedEntryPath = normalizeSkillEntry(item.skillSlug(), entry.getName());
                if (SkillPackagePolicy.SKILL_MD_PATH.equals(normalizedEntryPath)) {
                    hasSkillMarkdown = true;
                }
                if (isWorkbenchAdapterFile(normalizedEntryPath)) {
                    continue;
                }
                String outputPath = skillDirectory + "/" + normalizedEntryPath;
                if (!writtenEntries.add(outputPath)) {
                    throw new DomainBadRequestException("error.skillBundle.download.duplicateEntry", outputPath);
                }
                zipOutputStream.putNextEntry(new ZipEntry(outputPath));
                copyWithLimit(zipInputStream, zipOutputStream, copiedContentBytes, bundleSlug);
                zipOutputStream.closeEntry();
            }
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.skillBundle.download.invalidZip", item.skillSlug());
        }
        if (!hasSkillMarkdown) {
            throw new DomainBadRequestException("error.skillBundle.download.invalidSkill", item.skillSlug());
        }
    }

    private void copyWithLimit(InputStream inputStream,
                               OutputStream outputStream,
                               long[] copiedContentBytes,
                               String bundleSlug) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            copiedContentBytes[0] += read;
            if (copiedContentBytes[0] > maxContentBytes) {
                throw new DomainBadRequestException("error.skillBundle.download.tooLarge", bundleSlug);
            }
            outputStream.write(buffer, 0, read);
        }
    }

    private boolean isWorkbenchAdapterFile(String normalizedEntryPath) {
        int slashIndex = normalizedEntryPath.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? normalizedEntryPath.substring(slashIndex + 1) : normalizedEntryPath;
        String normalizedFileName = fileName.toUpperCase(Locale.ROOT);
        return normalizedFileName.equals("AGENTS.MD")
                || normalizedFileName.equals("CLAUDE.MD")
                || normalizedFileName.equals("GEMINI.MD");
    }

    private String normalizeSkillEntry(String skillSlug, String rawEntryName) {
        try {
            return SkillPackagePolicy.normalizeEntryPath(rawEntryName);
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.skillBundle.download.invalidZip", skillSlug);
        }
    }

    private String sanitizePathSegment(String value) {
        String sanitized = value == null ? "" : value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "-")
                .replaceAll("-+", "-");
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            return "skill";
        }
        return sanitized;
    }

    private String sanitizeFilename(String value) {
        String sanitized = value == null ? "" : value
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-")
                .replaceAll("\\s+", " ")
                .trim();
        return sanitized.isBlank() ? "expert-package" : sanitized;
    }
}
