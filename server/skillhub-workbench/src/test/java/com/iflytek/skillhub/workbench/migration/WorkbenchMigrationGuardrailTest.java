package com.iflytek.skillhub.workbench.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkbenchMigrationGuardrailTest {

    @Test
    void migrationCreatesOnlyWorkbenchTablesAndDoesNotAlterSkillTables() throws IOException {
        String sql = readMigration();
        String normalized = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertThat(normalized).contains("create table workbench_session");
        assertThat(normalized).contains("create table workbench_session_event");
        assertThat(normalized).contains("create table workbench_file_snapshot");
        assertThat(normalized).contains("create table workbench_mcp_binding");
        assertThat(normalized).contains("create table workbench_tool_approval");
        assertThat(normalized).contains("create table workbench_publish_candidate");
        assertThat(normalized).doesNotContain("alter table skill ");
        assertThat(normalized).doesNotContain("alter table skill_version");
        assertThat(normalized).doesNotContain("alter table skill_file");
        assertThat(normalized).doesNotContain("create table skill ");
        assertThat(normalized).doesNotContain("create table skill_version");
        assertThat(normalized).doesNotContain("create table skill_file");
    }

    @Test
    void migrationKeepsWorkbenchRelationsAsLogicalIdsWithoutForeignKeys() throws IOException {
        String normalized = readMigration().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertThat(normalized).contains("source_skill_id bigint");
        assertThat(normalized).contains("source_version_id bigint");
        assertThat(normalized).contains("skill_version_id bigint");
        assertThat(normalized).doesNotContain(" references ");
        assertThat(normalized).doesNotContain("foreign key");
    }

    @Test
    void migrationLeavesSourceSkillAndVersionConsistencyToApplicationPorts() throws IOException {
        String normalized = readMigration().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertThat(normalized).contains("logical references");
        assertThat(normalized).contains("enforced by application ports");
        assertThat(normalized).doesNotContain("validate_workbench_session_source_version");
        assertThat(normalized).doesNotContain("create trigger");
        assertThat(normalized).doesNotContain("select source_version.skill_id");
    }

    @Test
    void migrationRequiresPublishCandidateSkillVersionOnlyWhenPublished() throws IOException {
        String normalized = readMigration().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertThat(normalized).contains("(validation_status = 'published' and skill_version_id is not null)");
        assertThat(normalized).contains("(validation_status <> 'published' and skill_version_id is null)");
    }

    private String readMigration() throws IOException {
        Path migration = Path.of(System.getProperty("user.dir"))
                .getParent()
                .resolve("skillhub-app/src/main/resources/db/migration/V44__workbench_tables.sql");
        return Files.readString(migration);
    }
}
