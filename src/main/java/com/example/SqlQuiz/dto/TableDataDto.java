package com.example.SqlQuiz.dto;

import java.util.List;
import java.util.Map;

/**
 * 表格数据传输对象
 * 用于前端展示和编辑问题关联的表格数据
 */
public class TableDataDto {

    private String tablePrefix;
    private String setupSql;
    private List<TableInfo> tables;

    public TableDataDto() {}

    public TableDataDto(String tablePrefix, String setupSql, List<TableInfo> tables) {
        this.tablePrefix = tablePrefix;
        this.setupSql = setupSql;
        this.tables = tables;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    public String getSetupSql() {
        return setupSql;
    }

    public void setSetupSql(String setupSql) {
        this.setupSql = setupSql;
    }

    public List<TableInfo> getTables() {
        return tables;
    }

    public void setTables(List<TableInfo> tables) {
        this.tables = tables;
    }

    /**
     * 表格信息
     */
    public static class TableInfo {
        private String originalName;  // 原始表名（带前缀）
        private String displayName;   // 显示名称（去前缀）
        private List<String> columns; // 列名列表
        private List<Map<String, Object>> rows; // 数据行

        public TableInfo() {}

        public TableInfo(String originalName, String displayName, List<String> columns, List<Map<String, Object>> rows) {
            this.originalName = originalName;
            this.displayName = displayName;
            this.columns = columns;
            this.rows = rows;
        }

        public String getOriginalName() {
            return originalName;
        }

        public void setOriginalName(String originalName) {
            this.originalName = originalName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public List<String> getColumns() {
            return columns;
        }

        public void setColumns(List<String> columns) {
            this.columns = columns;
        }

        public List<Map<String, Object>> getRows() {
            return rows;
        }

        public void setRows(List<Map<String, Object>> rows) {
            this.rows = rows;
        }
    }
}
