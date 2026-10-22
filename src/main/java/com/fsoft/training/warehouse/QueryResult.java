package com.fsoft.training.warehouse;

import java.util.List;

public record QueryResult(List<ColumnInfo> columns, List<List<Object>> rows, boolean truncated) {
}
