package com.smart.retry.mybatis;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * @Author xiaoqiang
 * @Version DatabaseType.java, v 0.1 2025年11月15日 08:51 xiaoqiang
 * @Description: 数据库类型枚举。Starter 启动时通过 JDBC 元数据识别数据库产品，
 * 并映射到对应 MyBatis 配置资源；未适配的数据库返回 UNKNOWN。
 */
public enum DatabaseType {
    MYSQL("mysql", "MySQL", "classpath:smart-mybatis-config-mysql.xml"),
    POSTGRESQL("postgresql", "PostgreSQL", "classpath:smart-mybatis-config-postgresql.xml"),
    ORACLE("oracle", "Oracle","classpath:smart-mybatis-config-oracle.xml"),
    SQLSERVER("sqlserver", "Microsoft SQL Server",null),
    H2("h2", "H2",null),
    SQLITE("sqlite", "SQLite",null),
    UNKNOWN("unknown", "Unknown",null);

    private final String code;
    private final String name;

    private final String resource;

    DatabaseType(String code, String name, String resource) {
        this.code = code;
        this.name = name;
        this.resource = resource;
    }

    /** @return 配置文件使用的数据库编码 */
    public String getCode() { return code; }

    /** @return 展示用数据库产品名 */
    public String getName() { return name; }

    /** @return 对应 MyBatis 配置资源；未适配数据库返回 null */

    public String getResource() {
        return resource;
    }

    /**
     * 从数据源建立连接并读取数据库产品名，识别实际数据库类型。
     *
     * @param dataSource Spring 管理的数据源，必须可建立连接
     * @return 匹配的数据库类型；无法识别或未适配时返回 UNKNOWN
     */
    public static DatabaseType fromDataSource(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String productName = metaData.getDatabaseProductName().toLowerCase();

            for (DatabaseType type : values()) {
                if (productName.contains(type.name.toLowerCase())) {
                    return type;
                }
            }
        } catch (SQLException e) {
            // 处理异常
            //e.printStackTrace();
            throw new RuntimeException("Failed to determine database type", e);
        }
        return UNKNOWN;
    }
}

