package com.example.SqlQuiz.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    /**
     * 主数据库配置（业务数据库）
     * 用于存储用户信息、测验信息等业务数据
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary.hikari")
    public HikariDataSource primaryDataSource() {
        return primaryDataSourceProperties().initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public JdbcTemplate primaryJdbcTemplate(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 测试数据库配置（判题用）
     * 用于执行学生提交的SQL语句进行判题
     */
    @Bean
    @ConfigurationProperties("spring.datasource.test")
    public DataSourceProperties testDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("testDataSource")
    @ConfigurationProperties("spring.datasource.test.hikari")
    public HikariDataSource testDataSource() {
        return testDataSourceProperties().initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
    }

    @Bean("testJdbcTemplate")
    public JdbcTemplate testJdbcTemplate(@Qualifier("testDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}