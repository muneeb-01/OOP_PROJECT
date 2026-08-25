package org.example.models;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public class Receiver extends User {

    @Override
    public String getTableName() {
        return "receiver";
    }

    @Override
    public void showMenu() {
        printMenuBox("RECEIVER MANAGEMENT", new String[]{
            "1. Add Receiver",
            "2. Delete Receiver",
            "3. View All Receivers",
            "4. Find Receiver by ID",
            "5. Update Receiver",
            "Press 'e' or 'Esc' to go back"
        });
    }

    @Override
    public void handleChoice(int choice, Connection connection, Scanner scanner) {
        try {
            switch (choice) {
                case 1 -> addUser(connection, scanner);
                case 2 -> deleteUser(connection, scanner);
                case 3 -> findAll(connection);
                case 4 -> findById(connection, scanner);
                case 5 -> updateUser(connection, scanner);
                default -> System.out.println("  ✘ Invalid choice. Try again.");
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Database error: " + e.getMessage());
        }
    }

    @Override
    public void addUser(Connection connection, Scanner scanner) throws SQLException {
        name = getValidName(scanner);
        contactInfo = getValidContactInfo(scanner);

        String query = "INSERT INTO receiver (name, contact) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, name);
            stmt.setString(2, contactInfo);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                try (PreparedStatement idStmt = connection.prepareStatement(
                        "SELECT last_insert_rowid()");
                     ResultSet rs = idStmt.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("  ✔ Receiver added successfully with ID: " + rs.getInt(1));
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Error adding receiver: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public void deleteUser(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("  Enter Receiver ID to delete: ");
        int targetId = getValidId(scanner);

        String checkQuery = "SELECT EXISTS(SELECT 1 FROM orders WHERE receiverId = ?)";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
            checkStmt.setInt(1, targetId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 1) {
                    System.out.println("  ✘ Warning: Receiver ID " + targetId +
                            " has linked orders. Cannot delete.");
                    return;
                }
            }
        }

        String query = "DELETE FROM receiver WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, targetId);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("  ✔ Receiver with ID " + targetId + " deleted successfully.");
            } else {
                System.out.println("  ✘ No receiver found with ID " + targetId);
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Error deleting receiver: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public void updateUser(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("  Enter Receiver ID to update: ");
        int targetId = getValidId(scanner);

        if (!existsById(connection, targetId)) {
            System.out.println("  ✘ No receiver found with ID " + targetId);
            return;
        }

        String newName = getValidName(scanner);
        String newContact = getValidContactInfo(scanner);

        String query = "UPDATE receiver SET name = ?, contact = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, newName);
            stmt.setString(2, newContact);
            stmt.setInt(3, targetId);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("  ✔ Receiver ID " + targetId + " updated successfully.");
            } else {
                System.out.println("  ✘ Update failed. Receiver not found.");
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Error updating receiver: " + e.getMessage());
            throw e;
        }
    }

    private static void printMenuBox(String title, String[] options) {
        int w = 42;
        System.out.println("\n╔" + "═".repeat(w) + "╗");
        System.out.printf("║  %-" + (w - 2) + "s║%n", title);
        System.out.println("╠" + "═".repeat(w) + "╣");
        for (String opt : options) {
            System.out.printf("║  %-" + (w - 2) + "s║%n", opt);
        }
        System.out.println("╚" + "═".repeat(w) + "╝");
    }
}
