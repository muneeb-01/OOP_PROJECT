package org.example.models;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class ConnectDB {
    private Connection connection;

    public ConnectDB() {
        String url = "jdbc:sqlite:mydb.db";
        try {
            connection = DriverManager.getConnection(url);
            // Enable WAL mode for better concurrent read performance
            // Enable foreign key enforcement (SQLite disables it by default)
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode = WAL");
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA synchronous = NORMAL"); // safer than OFF, faster than FULL
            }
        } catch (SQLException e) {
            System.err.println("Failed to connect to the database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        return connection;
    }
}
