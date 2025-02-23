package cassmiggy.internal.engine;

import java.util.Set;

public class SchemaChangeDetector {

    private static final Set<String> DDL_KEYWORDS = Set.of("CREATE", "ALTER", "DROP");

    /**
     * True if the statement changes schema (starts with CREATE, ALTER, or DROP).
     */
    public boolean isDdl(String statement) {
        if (statement == null || statement.isBlank()) {
            return false;
        }
        String firstWord = statement.stripLeading().split("\\s+", 2)[0].toUpperCase();
        return DDL_KEYWORDS.contains(firstWord);
    }
}
