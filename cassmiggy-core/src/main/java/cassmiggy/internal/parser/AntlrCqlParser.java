package cassmiggy.internal.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cassmiggy.exception.ParserException;
import cassmiggy.spi.MigrationParser;

/**
 * Splits CQL content into individual statements using the a cql grammar https://github.com/antlr/grammars-v4/tree/master/cql3.
 */
public class AntlrCqlParser implements MigrationParser {

    private static final Logger log = LoggerFactory.getLogger(AntlrCqlParser.class);

    @Override
    public List<String> parse(String cqlContent) {
        if (cqlContent == null || cqlContent.isBlank()) {
            log.debug("Empty CQL content, returning empty list");
            return List.of();
        }

        log.debug("Parsing CQL content ({} characters)", cqlContent.length());

        try {
            return parseWithAntlr(cqlContent);
        } catch (Exception e) {
            throw new ParserException("Failed to parse CQL content: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> parseFile(Path cqlFile) {
        log.debug("Parsing CQL file: {}", cqlFile);
        try {
            String content = Files.readString(cqlFile, StandardCharsets.UTF_8);
            return parse(content);
        } catch (IOException e) {
            throw new ParserException(cqlFile, "Failed to read CQL file", e);
        }
    }

    private List<String> parseWithAntlr(String cqlContent) {
        CqlLexer lexer = new CqlLexer(CharStreams.fromString(cqlContent));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CqlParser parser = new CqlParser(tokens);

        List<String> syntaxErrors = new ArrayList<>();
        lexer.removeErrorListeners();
        parser.removeErrorListeners();

        BaseErrorListener errorListener = new BaseErrorListener() {
            @Override
            public void syntaxError(
                    Recognizer<?, ?> recognizer,
                    Object offendingSymbol,
                    int line,
                    int charPositionInLine,
                    String msg,
                    RecognitionException e) {

                String cleanMsg = truncateErrorMessage(msg);
                syntaxErrors.add("line " + line + ":" + charPositionInLine + " " + cleanMsg);
            }
        };
        lexer.addErrorListener(errorListener);
        parser.addErrorListener(errorListener);

        CqlParser.RootContext rootContext = parser.root();

        if (!syntaxErrors.isEmpty()) {
            throw new ParserException("Invalid CQL syntax: " + String.join("; ", syntaxErrors));
        }

        List<String> statements = new ArrayList<>();

        if (rootContext.cqls() != null) {
            for (CqlParser.CqlContext cqlContext : rootContext.cqls().cql()) {
                String stmtText = extractStatementText(cqlContext, tokens);
                if (stmtText != null && !stmtText.isBlank()) {
                    statements.add(stmtText);
                }
            }
        }

        log.debug("Parsed {} statements with ANTLR", statements.size());
        return statements;
    }

    private String extractStatementText(CqlParser.CqlContext context, CommonTokenStream tokens) {
        if (context.getStart() == null || context.getStop() == null) {
            return normalizeStatement(context.getText());
        }

        int startIndex = context.getStart().getStartIndex();
        int stopIndex = context.getStop().getStopIndex();

        if (startIndex < 0 || stopIndex < 0) {
            return normalizeStatement(context.getText());
        }

        Interval interval = new Interval(startIndex, stopIndex);
        String text = tokens.getTokenSource().getInputStream().getText(interval);

        return normalizeStatement(text);
    }

    private String normalizeStatement(String statement) {
        if (statement == null) {
            return null;
        }

        String trimmed = statement.strip();

        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).stripTrailing();
        }

        return trimmed.isBlank() ? null : trimmed;
    }


    private static String truncateErrorMessage(String msg) {
        if (msg == null) {
            return null;
        }
        // ANTLR messages embed the full offending input after "at input '"; truncate it so
        // error messages stay readable rather than echoing the entire remaining statement.
        int inputIdx = msg.indexOf("at input '");
        if (inputIdx > 0) {
            String prefix = msg.substring(0, inputIdx + 10);
            String rest = msg.substring(inputIdx + 10);
            int endQuote = rest.indexOf("'");
            if (endQuote > 50) {
                return prefix + rest.substring(0, 50) + "...'";
            }
        }
        if (msg.length() > 100) {
            return msg.substring(0, 100) + "...";
        }
        return msg;
    }
}
