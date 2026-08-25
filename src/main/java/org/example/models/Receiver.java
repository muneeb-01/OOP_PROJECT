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
        System.out.println("\nReceiver Management Menu:");
        System.out.println("1. Add Receiver");
        System.out.println("2. Delete Receiver");
        System.out.println("3. View All Receivers");
        System.out.println("4. Find Receiver by ID");
        System.out.println("5. Update Receiver");
        System.out.println("Press 'e' or 'Esc' to go back");
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
                default -> System.out.println("Invalid choice. Try again.");
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
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
                        System.out.println("Receiver added successfully with ID: " + rs.getInt(1));
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error adding receiver: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public void deleteUser(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("Enter Receiver ID to delete: ");
        int targetId = getValidId(scanner);

        // Guard: check if any orders reference this receiver
        String checkQuery = "SELECT EXISTS(SELECT 1 FROM orders WHERE receiverId = ?)";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
            checkStmt.setInt(1, targetId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 1) {
                    System.out.println("Warning: Receiver ID " + targetId +
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
                System.out.println("Receiver with ID " + targetId + " deleted successfully.");
            } else {
                System.out.println("No receiver found with ID " + targetId);
            }
        } catch (SQLException e) {
            System.out.println("Error deleting receiver: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public void updateUser(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("Enter Receiver ID to update: ");
        int targetId = getValidId(scanner);

        if (!existsById(connection, targetId)) {
            System.out.println("No receiver found with ID " + targetId);
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
                System.out.println("Receiver ID " + targetId + " updated successfully.");
            } else {
                System.out.println("Update failed. Receiver not found.");
            }
        } catch (SQLException e) {
            System.out.println("Error updating receiver: " + e.getMessage());
            throw e;
        }
    }
}
