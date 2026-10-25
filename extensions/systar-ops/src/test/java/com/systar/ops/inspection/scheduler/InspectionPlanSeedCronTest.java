package com.systar.ops.inspection.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InspectionScheduler parses plan crons with Spring's CronExpression, which
 * requires 6 fields (second minute hour day-of-month month day-of-week). The
 * simulator seed once carried 5-field Unix crons ("0 8 * * *"), so both demo
 * plans were skipped every minute with "Invalid inspection cron expression"
 * and never generated tasks. This guard pins every seeded cron to what the
 * scheduler can actually parse, in both SQL dialects.
 */
class InspectionPlanSeedCronTest {

    private static final Pattern PLAN_ROW =
            Pattern.compile("\\(\\s*\\d+,\\s*'[^']*',\\s*'[^']*',\\s*'([^']*)'");

    @Test
    void h2SeedCronsParseAsSpringCron() throws Exception {
        assertSeedCrons("sql/h2/data/03-simulator.sql");
    }

    @Test
    void mysqlSeedCronsParseAsSpringCron() throws Exception {
        assertSeedCrons("sql/mysql/data/03-simulator.sql");
    }

    private void assertSeedCrons(String relativePath) throws Exception {
        // seeds live at repo root; surefire CWD varies (module dir or reactor
        // root depending on invocation), so walk up until the seed resolves
        Path dir = Path.of("");
        Path file = null;
        while (dir != null) {
            Path candidate = dir.resolve(relativePath);
            if (Files.exists(candidate)) { file = candidate; break; }
            dir = dir.toAbsolutePath().getParent();
        }
        assertTrue(file != null, "seed file not found walking up from CWD: " + relativePath);
        String text = Files.readString(file);

        int block = text.indexOf("t_inspection_plan (");
        assertTrue(block >= 0, "no t_inspection_plan seed block in " + relativePath);
        int end = text.indexOf(';', block);
        Matcher rows = PLAN_ROW.matcher(text.substring(block, end));

        int plans = 0;
        while (rows.find()) {
            String cron = rows.group(1);
            assertDoesNotThrow(() -> CronExpression.parse(cron),
                    "seeded plan cron is not parseable by Spring CronExpression: " + cron);
            plans++;
        }
        assertTrue(plans >= 2, "expected at least 2 seeded plans, matched " + plans);
    }
}
