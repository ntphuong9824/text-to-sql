package com.fsoft.training.warehouse;

/** The query plan touches something outside the allowed surface. Deliberately not a SqlExecutionException. */
public class SqlPolicyViolationException extends RuntimeException {

    public SqlPolicyViolationException(String reason) {
        super(reason);
    }
}
