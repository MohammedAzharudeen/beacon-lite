package com.beacon.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

/** The changelog applies, rolls back completely and applies again. */
class SchemaRollbackTest {

  private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

  @Test
  void changelog_rollsBackFully_andReapplies() throws Exception {
    String url = "jdbc:h2:mem:rollback-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      Database database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);

      liquibase.update("");
      int applied = liquibase.getDatabase().getRanChangeSetList().size();
      assertThat(applied).isPositive();
      assertThat(appTables(connection)).contains("store", "snapshot", "product", "insight_report");

      liquibase.rollback(applied, "");
      assertThat(appTables(connection)).isEmpty();

      liquibase.update("");
      assertThat(appTables(connection)).contains("store", "insight_report");
    }
  }

  /** Application tables in the default schema, excluding Liquibase's own. */
  private static List<String> appTables(Connection connection) throws Exception {
    List<String> names = new ArrayList<>();
    try (ResultSet rs =
        connection
            .createStatement()
            .executeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC'")) {
      while (rs.next()) {
        String name = rs.getString(1).toLowerCase(Locale.ROOT);
        if (!name.startsWith("databasechangelog")) {
          names.add(name);
        }
      }
    }
    return names;
  }
}
