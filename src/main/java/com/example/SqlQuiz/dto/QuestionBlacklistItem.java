package com.example.SqlQuiz.dto;

/**
 * 题目黑名单项
 * 用于记录已存在的题目，避免AI生成重复题目
 */
public class QuestionBlacklistItem {

    /**
     * 归一化后的题目内容（去除多余空格、换行等）
     */
    private final String content;

    /**
     * 归一化后的期望SQL（统一大小写、去除多余空格）
     */
    private final String expectedSql;

    /**
     * 原始题目ID（可选，用于调试）
     */
    private final Long originalQuestionId;

    public QuestionBlacklistItem(String content, String expectedSql) {
        this.content = normalizeContent(content);
        this.expectedSql = normalizeSql(expectedSql);
        this.originalQuestionId = null;
    }

    public QuestionBlacklistItem(String content, String expectedSql, Long originalQuestionId) {
        this.content = normalizeContent(content);
        this.expectedSql = normalizeSql(expectedSql);
        this.originalQuestionId = originalQuestionId;
    }

    /**
     * 归一化题目内容
     * - 去除多余空格
     * - 去除换行符
     * - 转换为小写
     */
    private String normalizeContent(String content) {
        if (content == null) return "";
        return content
                .toLowerCase()
                .replaceAll("\\s+", " ")
                .replaceAll("[\\r\\n]+", " ")
                .trim();
    }

    /**
     * 归一化SQL语句
     * - 转换为大写（关键字）
     * - 去除多余空格
     * - 去除换行符
     * - 去除分号
     */
    private String normalizeSql(String sql) {
        if (sql == null) return "";
        return sql
                .toUpperCase()
                .replaceAll("\\s+", " ")
                .replaceAll("[\\r\\n]+", " ")
                .replace(";", "")
                .trim();
    }

    /**
     * 计算与另一个黑名单项的相似度
     * 使用简单的编辑距离算法
     */
    public double calculateSimilarity(QuestionBlacklistItem other) {
        double contentSim = calculateLevenshteinSimilarity(this.content, other.content);
        double sqlSim = calculateLevenshteinSimilarity(this.expectedSql, other.expectedSql);

        // SQL相似度权重更高
        return contentSim * 0.3 + sqlSim * 0.7;
    }

    /**
     * 计算Levenshtein相似度 (0-1之间)
     */
    private double calculateLevenshteinSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;

        int maxLength = Math.max(s1.length(), s2.length());
        int distance = levenshteinDistance(s1, s2);

        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * 计算Levenshtein距离
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * 判断是否可能是重复题目
     * 相似度阈值：0.85
     */
    public boolean isPossibleDuplicate(QuestionBlacklistItem other) {
        return calculateSimilarity(other) >= 0.85;
    }

    // Getters
    public String getContent() {
        return content;
    }

    public String getExpectedSql() {
        return expectedSql;
    }

    public Long getOriginalQuestionId() {
        return originalQuestionId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        QuestionBlacklistItem that = (QuestionBlacklistItem) obj;
        return content.equals(that.content) && expectedSql.equals(that.expectedSql);
    }

    @Override
    public int hashCode() {
        return content.hashCode() * 31 + expectedSql.hashCode();
    }

    @Override
    public String toString() {
        return "QuestionBlacklistItem{" +
                "content='" + content + '\'' +
                ", expectedSql='" + expectedSql + '\'' +
                '}';
    }
}
