package com.spotmeet.backend.config;

import org.springframework.core.env.Environment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the application database on startup when it does not exist yet.
 * Runs before the connection pool is opened, using the same URL, user and password the server will use.
 */
public final class DatabaseCreator {

    private DatabaseCreator() {
    }

    public static void createIfMissing(Environment env) {
        String url = env.getProperty("spring.datasource.url");
        String user = env.getProperty("spring.datasource.username");
        String password = env.getProperty("spring.datasource.password");
        if (url == null || !url.startsWith("jdbc:postgresql://")) {
            return;
        }

        int slash = url.lastIndexOf('/');
        int query = url.indexOf('?', slash);
        String dbName = query == -1 ? url.substring(slash + 1) : url.substring(slash + 1, query);
        // The default "postgres" database always exists, so it is used to create the application one
        String adminUrl = url.substring(0, slash + 1) + "postgres";

        try (Connection conn = DriverManager.getConnection(adminUrl, user, password);
             PreparedStatement check = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            check.setString(1, dbName);
            try (ResultSet rs = check.executeQuery()) {
                if (!rs.next()) {
                    // CREATE DATABASE does not accept parameters, and the name comes from the server's own .env
                    try (Statement create = conn.createStatement()) {
                        create.execute("CREATE DATABASE \"" + dbName + "\"");
                    }
                    System.out.println("Banco " + dbName + " criado.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Nao foi possivel verificar o banco " + dbName + ": " + e.getMessage());
        }
    }
}
