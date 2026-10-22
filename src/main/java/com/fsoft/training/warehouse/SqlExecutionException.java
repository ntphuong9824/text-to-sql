package com.fsoft.training.warehouse;

/** Postgres rejected or failed the query; the message is the original database error (used for self-repair). */
public class SqlExecutionException extends RuntimeException {

    public SqlExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
