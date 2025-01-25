package cassmiggy.spi;

import java.util.List;

import cassmiggy.model.MigrationFile;

/**
 * Interface for discovering migration files.
 */
public interface MigrationDiscovery {

    /**
     * @return list of discovered migrations sorted by version
     */
    List<MigrationFile> discover();

    String getContent(MigrationFile migration);
}
