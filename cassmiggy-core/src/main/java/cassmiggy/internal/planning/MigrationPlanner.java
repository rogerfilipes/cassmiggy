package cassmiggy.internal.planning;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cassmiggy.model.MigrationFile;
import cassmiggy.model.MigrationRecord;

/**
 * Computes the list of migrations still to be applied.
 */
public class MigrationPlanner {

    public List<MigrationFile> filterPending(List<MigrationFile> discovered, List<MigrationRecord> applied) {
        Map<String, MigrationRecord> appliedMap = new HashMap<>();
        for (MigrationRecord m : applied) {
            appliedMap.put(m.getPath(), m);
        }

        return discovered.stream()
                .filter(d -> {
                    MigrationRecord appliedMigration = appliedMap.get(d.getPath());
                    if (appliedMigration == null) {
                        return true;
                    }
                    return appliedMigration.isFailed();
                })
                .toList();
    }
}
