package cassmiggy.spi;

import java.nio.file.Path;
import java.util.List;

/**
 * Parses CQL content from a string into individual statements.
 */
public interface MigrationParser {


    List<String> parse(String cqlContent);


    List<String> parseFile(Path cqlFile);
}
