package cassmiggy.internal.engine;

import java.time.Duration;

public final class ExecutionResult {

    private final String filename;
    private final int statementCount;
    private final Duration executionTime;
    private final boolean success;

    public ExecutionResult(String filename, int statementCount, Duration executionTime, boolean success) {
        this.filename = filename;
        this.statementCount = statementCount;
        this.executionTime = executionTime;
        this.success = success;
    }

    public String getFilename() {
        return filename;
    }

    public int getStatementCount() {
        return statementCount;
    }

    public Duration getExecutionTime() {
        return executionTime;
    }

    public boolean isSuccess() {
        return success;
    }
}
