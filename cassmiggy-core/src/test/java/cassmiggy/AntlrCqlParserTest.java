package cassmiggy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cassmiggy.exception.ParserException;
import cassmiggy.internal.parser.AntlrCqlParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class AntlrCqlParserTest {

    private final AntlrCqlParser parser = new AntlrCqlParser();

    @Test
    void emptyContentReturnsNoStatements() {
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse("   \n  ")).isEmpty();
    }

    @Test
    void splitsMultipleStatements() {
        String cql = "CREATE TABLE users (id uuid PRIMARY KEY, email text);\n"
                + "CREATE INDEX users_email_idx ON users (email);\n"
                + "INSERT INTO users (id, email) VALUES (now(), 'a@b.com');";

        List<String> statements = parser.parse(cql);

        assertThat(statements).hasSize(3);
        assertThat(statements.get(0)).startsWith("CREATE TABLE users");
        assertThat(statements.get(1)).startsWith("CREATE INDEX");
        assertThat(statements.get(2)).startsWith("INSERT INTO users");
    }

    @Test
    void ignoresCommentsAndBlankLines() {
        String cql = "-- create the table\n"
                + "CREATE TABLE foo (id int PRIMARY KEY);\n\n"
                + "// another comment\n"
                + "CREATE TABLE bar (id int PRIMARY KEY);";

        assertThat(parser.parse(cql)).hasSize(2);
    }

    @Test
    void keepsSemicolonsInsideStringLiterals() {
        String cql = "INSERT INTO notes (id, body) VALUES (1, 'a; b; c');";

        List<String> statements = parser.parse(cql);

        assertThat(statements).hasSize(1);
        assertThat(statements.get(0)).contains("'a; b; c'");
    }

    @Test
    void skipsEmptyStatementsAndTrailingSeparators() {
        String cql = "CREATE TABLE foo (id int PRIMARY KEY);;\n;\nCREATE TABLE bar (id int PRIMARY KEY);";

        List<String> statements = parser.parse(cql);

        assertThat(statements).hasSize(2);
        assertThat(statements.get(0)).startsWith("CREATE TABLE foo");
        assertThat(statements.get(1)).startsWith("CREATE TABLE bar");
    }

    @Test
    void stripsTrailingSemicolonFromStatementText() {
        List<String> statements = parser.parse("CREATE TABLE foo (id int PRIMARY KEY);");

        assertThat(statements).hasSize(1);
        assertThat(statements.get(0)).doesNotEndWith(";");
    }

    @Test
    void invalidCqlThrowsParserException() {
        assertThatThrownBy(() -> parser.parse("THIS IS NOT VALID CQL @@@;"))
                .isInstanceOf(ParserException.class);
    }
}
