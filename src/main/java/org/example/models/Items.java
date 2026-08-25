package org.example.models;

import java.sql.*;
import java.util.Scanner;

public class Items implements EntityHandler {
    private int itemId;
    private String name;
    private int pricePerUnit;

    @Override
    public void showMenu() {
        System.out.println("\nItems Management Menu:");
        System.out.println("1. Add Item");
        System.out.println("2. Delete Item");
        System.out.println("3. View All Items");
        System.out.println("4. Find Item by ID");
        System.out.println("5. Update Item");
        System.out.println("Press 'e' or 'Esc' to go back");
    }

    @Override
    public void handleChoice(int choice, Connection connection, Scanner scanner) {
        try {
            switch (choice) {
                case 1 -> addItem(connection, scanner);
                case 2 -> deleteItems(connection, scanner);
                case 3 -> findAll(connection);
                case 4 -> findById(connection, scanner);
                case 5 -> updateItem(connection, scanner);
                default -> System.out.println("Invalid choice. Please try again.");
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    // ── Input Helpers ─────────────────────────────────────────────────────────────
    public int getValidInt(Scanner scanner) {
        while (true) {
            try {
                int val = Integer.parseInt(scanner.nextLine().trim());
                if (val <= 0) {
                    System.out.println("Must be a positive number.");
                } else {
                    return val;
                }
            } catch (NumberFormatException e) {
                System.out.println("Error: Invalid input. Please enter a valid integer.");
            }
        }
    }

    public String getValidString(Scanner scanner) {
        while (true) {
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                System.out.println("Error: Field cannot be empty.");
            } else {
                return input;
            }
        }
    }

    // ── Add Item ──────────────────────────────────────────────────────────────────
    public void addItem(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("Enter number of raw materials for this item: ");
        int length = getValidInt(scanner);
        int[] rawMaterialIds = getRawMaterialIds(scanner, length);

        if (!validateRawMaterials(connection, rawMaterialIds)) {
            System.out.println("One or more invalid raw material IDs. Cannot add item.");
            return;
        }

        System.out.print("Enter Item Name: ");
        name = getValidString(scanner);
        System.out.print("Enter Price per Unit: ");
        pricePerUnit = getValidInt(scanner);

        insertItemIntoDatabase(connection);
        linkRawMaterialsToItem(connection, rawMaterialIds, itemId);
    }

    // ── Delete Item ───────────────────────────────────────────────────────────────
    public void deleteItems(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("Enter Item ID to delete: ");
        int itemIdToDelete = getValidInt(scanner);

        // Guard: check if item is referenced by any order or finish good
        String checkQuery = """
                SELECT EXISTS(
                    SELECT 1 FROM orders WHERE itemId = ?
                    UNION ALL
                    SELECT 1 FROM finish_goods WHERE itemId = ?
                )
                """;
        try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
            checkStmt.setInt(1, itemIdToDelete);
            checkStmt.setInt(2, itemIdToDelete);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 1) {
                    System.out.println("Warning: Item ID " + itemIdToDelete +
                            " is referenced by orders/finish goods. Cannot delete.");
                    return;
                }
            }
        }

        // Delete junction table entries first, then item (cascade-safe)
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement jStmt = connection.prepareStatement(
                    "DELETE FROM items_raw_materials WHERE itemId = ?")) {
                jStmt.setInt(1, itemIdToDelete);
                jStmt.executeUpdate();
            }
            try (PreparedStatement iStmt = connection.prepareStatement(
                    "DELETE FROM items WHERE itemId = ?")) {
                iStmt.setInt(1, itemIdToDelete);
                int rows = iStmt.executeUpdate();
                if (rows > 0) {
                    System.out.println("Item ID " + itemIdToDelete + " deleted successfully.");
                } else {
                    System.out.println("No item found with ID " + itemIdToDelete);
                }
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            System.out.println("Error deleting item: " + e.getMessage());
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    // ── Find All ──────────────────────────────────────────────────────────────────
    public void findAll(Connection connection) throws SQLException {
        // Also fetch linked raw material count via subquery for better info
        String query = """
                SELECT i.itemId, i.name, i.price_per_unit,
                       (SELECT COUNT(*) FROM items_raw_materials irm WHERE irm.itemId = i.itemId) AS raw_count,
                       i.createdAt
                FROM items i
                ORDER BY i.itemId
                """;
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            System.out.printf("%n%-8s  %-20s  %-14s  %-12s  %s%n",
                    "Item ID", "Name", "Price/Unit", "Raw Matls", "Created At");
            System.out.println("-".repeat(75));

            boolean hasRows = false;
            while (rs.next()) {
                hasRows = true;
                System.out.printf("%-8d  %-20s  %-14d  %-12d  %s%n",
                        rs.getInt("itemId"),
                        rs.getString("name"),
                        rs.getInt("price_per_unit"),
                        rs.getInt("raw_count"),
                        rs.getString("createdAt"));
            }
            if (!hasRows) System.out.println("No items found.");
        }
    }

    // ── Find By ID ────────────────────────────────────────────────────────────────
    public void findById(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("Enter Item ID: ");
        int itemIdToFind = getValidInt(scanner);

        // Single query JOINed with raw materials for full detail
        String itemQuery = "SELECT itemId, name, price_per_unit, createdAt FROM items WHERE itemId = ? LIMIT 1";
        String rmQuery = """
                SELECT rm.id, rm.name AS rm_name, rm.quantity, rm.price
                FROM items_raw_materials irm
                JOIN raw_materials rm ON irm.raw_material_id = rm.id
                WHERE irm.itemId = ?
                """;

        try (PreparedStatement stmt = connection.prepareStatement(itemQuery)) {
            stmt.setInt(1, itemIdToFind);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    System.out.printf("%nItem ID: %d | Name: %s | Price/Unit: %d | Created: %s%n",
                            rs.getInt("itemId"),
                            rs.getString("name"),
                            rs.getInt("price_per_unit"),
                            rs.getString("createdAt"));
                    System.out.println("Raw Materials:");
                    System.out.printf("  %-8s  %-20s  %-10s  %-10s%n", "RM ID", "Name", "Price", "Stock");
                    System.out.println("  " + "-".repeat(55));
                    try (PreparedStatement rmStmt = connection.prepareStatement(rmQuery)) {
                        rmStmt.setInt(1, itemIdToFind);
                        try (ResultSet rmRs = rmStmt.executeQuery()) {
                            boolean anyRm = false;
                            while (rmRs.next()) {
                                anyRm = true;
                                System.out.printf("  %-8d  %-20s  %-10d  %-10d%n",
                                        rmRs.getInt("id"),
                                        rmRs.getString("rm_name"),
                                        rmRs.getInt("price"),
                                        rmRs.getInt("quantity"));
                            }
                            if (!anyRm) System.out.println("  No raw materials linked.");
                        }
                    }
                } else {
                    System.out.println("No item found with ID " + itemIdToFind);
                }
            }
        }
    }

    // ── Update Item ───────────────────────────────────────────────────────────────
    public void updateItem(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("Enter Item ID to update: ");
        int targetId = getValidInt(scanner);

        // Check existence with EXISTS
        String checkQuery = "SELECT EXISTS(SELECT 1 FROM items WHERE itemId = ?)";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
            checkStmt.setInt(1, targetId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    System.out.println("No item found with ID " + targetId);
                    return;
                }
            }
        }

        System.out.print("Enter new Item Name: ");
        String newName = getValidString(scanner);
        System.out.print("Enter new Price per Unit: ");
        int newPrice = getValidInt(scanner);

        String query = "UPDATE items SET name = ?, price_per_unit = ? WHERE itemId = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, newName);
            stmt.setInt(2, newPrice);
            stmt.setInt(3, targetId);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("Item ID " + targetId + " updated successfully.");
            } else {
                System.out.println("Update failed. Item not found.");
            }
        } catch (SQLException e) {
            System.out.println("Error updating item: " + e.getMessage());
            throw e;
        }
    }

    // ── Private Helpers ───────────────────────────────────────────────────────────
    private int[] getRawMaterialIds(Scanner scanner, int length) {
        int[] ids = new int[length];
        for (int i = 0; i < length; i++) {
            System.out.print("Enter Raw Material " + (i + 1) + " ID: ");
            ids[i] = getValidInt(scanner);
        }
        return ids;
    }

    private boolean validateRawMaterials(Connection connection, int[] rawMaterialIds) throws SQLException {
        // Validate all in one round-trip using IN clause
        if (rawMaterialIds.length == 0) return true;
        StringBuilder placeholders = new StringBuilder("?");
        for (int i = 1; i < rawMaterialIds.length; i++) placeholders.append(",?");

        String query = "SELECT COUNT(*) FROM raw_materials WHERE id IN (" + placeholders + ")";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            for (int i = 0; i < rawMaterialIds.length; i++) {
                stmt.setInt(i + 1, rawMaterialIds[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == rawMaterialIds.length;
                }
            }
        }
        return false;
    }

    private void insertItemIntoDatabase(Connection connection) throws SQLException {
        String insertItemQuery = "INSERT INTO items (name, price_per_unit) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insertItemQuery, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setInt(2, pricePerUnit);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        itemId = keys.getInt(1);
                        System.out.println("Item added successfully with ID: " + itemId);
                    }
                }
            } else {
                System.out.println("Failed to add item.");
                throw new SQLException("Item insert returned 0 rows.");
            }
        }
    }

    private void linkRawMaterialsToItem(Connection connection, int[] rawMaterialIds, int itemId) throws SQLException {
        // Use batch insert with ON CONFLICT IGNORE for efficiency
        String insertQuery = "INSERT OR IGNORE INTO items_raw_materials (itemId, raw_material_id) VALUES (?, ?)";
        connection.setAutoCommit(false);
        try (PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
            for (int rawMaterialId : rawMaterialIds) {
                stmt.setInt(1, itemId);
                stmt.setInt(2, rawMaterialId);
                stmt.addBatch();
            }
            stmt.executeBatch();
            connection.commit();
            System.out.println("Raw materials linked to item successfully.");
        } catch (SQLException e) {
            connection.rollback();
            System.out.println("Error linking raw materials: " + e.getMessage());
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }
}