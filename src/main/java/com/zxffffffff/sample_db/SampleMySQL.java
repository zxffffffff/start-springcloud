package com.zxffffffff.sample_db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zxffffffff.sample_tools.SampleTools;

import javax.sql.DataSource;
import java.sql.*;

public class SampleMySQL {
    DataSource dataSource;

    /**
     * 初始化 MySQL 连接池
     *
     * @param host 地址 "localhost"
     * @param user 账号 "root"
     * @param pwd  密码 "123456"
     * @param db   数据库名 "test_db"
     */
    public SampleMySQL(String host, String user, String pwd, String db) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":3306/" + db);
        config.setUsername(user);
        config.setPassword(pwd);
        config.addDataSourceProperty("connectionTimeout", "1000"); // 连接超时：1秒
        config.addDataSourceProperty("idleTimeout", "60000"); // 空闲超时：60秒
        config.addDataSourceProperty("maximumPoolSize", "10"); // 最大连接数：10

        this.dataSource = new HikariDataSource(config);
    }

    /**
     * 注册
     *
     * @param user_name    （必填）用户名
     * @param user_email   （可选为空）邮箱
     * @param user_phone   （必填）手机号
     * @param user_pwd_md5 （必填）密码-MD5
     * @return user_id
     */
    public long signup(String user_name, String user_email, String user_phone, String user_pwd_md5) {
        // [0] 检查参数
        if (!SampleTools.checkIsValidName(user_name)) {
            throw new RuntimeException("invalid user name");
        }
        if (!user_email.isEmpty() && !SampleTools.checkIsValidMail(user_email)) {
            throw new RuntimeException("invalid user email");
        }
        if (!SampleTools.checkIsValidPhone(user_phone)) {
            throw new RuntimeException("invalid user phone");
        }
        if (user_pwd_md5.length() != 32) {
            throw new RuntimeException("invalid password MD5");
        }

        // [1] 检查user是否存在
        String sql = "SELECT id FROM user_account_pwd WHERE user_name=? or user_email=? or user_phone=?";
        try (Connection conn = this.dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int index = 1; // 注意：索引从1开始
                ps.setString(index++, user_name);
                ps.setString(index++, user_email);
                ps.setString(index, user_phone);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new RuntimeException("user name/email/phone exists");
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // [2] 插入user
        sql = "INSERT INTO user_account_pwd (create_time,update_time,user_id,user_password,user_name,user_phone,user_email) VALUES (?,?,?,?,?,?,?);";
        long user_id = SampleTools.createSnowFlakeID();
        String user_password = SampleTools.getSaltPassword(user_pwd_md5);
        java.util.Date date = new Date(System.currentTimeMillis());
        try (Connection conn = this.dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int index = 1; // 注意：索引从1开始
                ps.setTimestamp(index++, new java.sql.Timestamp(date.getTime()));
                ps.setTimestamp(index++, new java.sql.Timestamp(date.getTime()));
                ps.setLong(index++, user_id);
                ps.setString(index++, user_password);
                ps.setString(index++, user_name);
                ps.setString(index++, user_phone);
                if (user_email.isEmpty())
                    ps.setNull(index, Types.VARCHAR);
                else
                    ps.setString(index, user_email);
                int ret = ps.executeUpdate();
                if (ret != 1) {
                    throw new RuntimeException("insert err");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return user_id;
    }

    /** 登陆
     * @param user_name_email_phone 支持多种登陆
     * @param user_pwd_md5 密码-MD5
     * @return user_id
     */
    public long login(String user_name_email_phone, String user_pwd_md5) {
        // [0] 检查参数
        if (user_pwd_md5.length() != 32) {
            throw new RuntimeException("invalid password MD5");
        }

        // [1] 判断类型
        String sql = "SELECT user_id FROM user_account_pwd WHERE user_password=? AND ";
        if (SampleTools.checkIsValidMail(user_name_email_phone)) {
            sql += "user_email=?;";
        } else if (SampleTools.checkIsValidPhone(user_name_email_phone)) {
            sql += "user_phone=?;";
        } else if (SampleTools.checkIsValidName(user_name_email_phone)) {
            sql += "user_name=?;";
        } else {
            throw new RuntimeException("user name/email/phone err");
        }
        String user_password = SampleTools.getSaltPassword(user_pwd_md5);

        try {
            try (Connection conn = this.dataSource.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    int index = 1; // 注意：索引从1开始
                    ps.setString(index++, user_password);
                    ps.setString(index, user_name_email_phone);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getLong(1);
                        } else {
                            throw new RuntimeException("user name or password err");
                        }
                    }
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 清空表，用于测试
     * drop     DDL 删表数据+表结构
     * truncate DDL 删表数据，保留表结构
     * delete   DML 删表行，配合where使用，可回滚
     */
    public void forceDeleteUserDB() {
        String sql = "TRUNCATE user_account_pwd;";
        try (Connection conn = this.dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
