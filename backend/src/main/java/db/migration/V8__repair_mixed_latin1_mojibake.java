package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Re-runs the V7 decoder after it learned to repair a mojibake fragment inside
 * otherwise valid Unicode text. This primarily covers notifications generated
 * from a corrupted exam title before V7 repaired the exams table.
 */
public class V8__repair_mixed_latin1_mojibake extends BaseJavaMigration {
    private static final Logger LOG = LoggerFactory.getLogger(V8__repair_mixed_latin1_mojibake.class);

    @Override
    public void migrate(Context context) throws Exception {
        int total = 0;
        for (V7__repair_latin1_mojibake.TextColumn column : V7__repair_latin1_mojibake.COLUMNS) {
            int repaired = V7__repair_latin1_mojibake.repairColumn(context.getConnection(), column);
            total += repaired;
            if (repaired > 0) {
                LOG.info("Repaired {} mixed mojibake value(s) in {}.{}",
                        repaired, column.table(), column.name());
            }
        }
        LOG.info("Mixed mojibake repair completed; {} value(s) changed", total);
    }
}
