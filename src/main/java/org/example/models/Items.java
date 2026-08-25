package org.example.models;

import java.sql.*;
import java.util.Scanner;

import static org.example.models.TablePrinter.*;

public class Items implements EntityHandler {
    private int itemId;
    private String name;
    private int pricePerUnit;

    // Column configs
    private static final String[] HEADERS_LIST = {"Item ID", "Name", "Price/Unit", "Raw Matls", "Created At"};
    private static final int[]    WIDTHS_LIST  = {7, 22, 10, 9, 22};
    private static final Align[]  ALIGNS_LIST  = {Align.RIGHT, Align.LEFT, Align.RIGHT,
                                                   Align.RIGHT, Align.LEFT};

    private static final String[] HEADERS_RM   = {"RM ID", "Name", "Price", "Stock"};
    private static final int[]    WIDTHS_RM    = {6, 22, 8, 8};
    private static final Align[]  ALIGNS_RM    = {Align.RIGHT, Align.LEFT, Align.RIGHT, Align.RIGHT};

    @Override
    public void showMenu() {
        String[] options = {
            "1. Add Item",
            "2. Delete Item",
            "3. View All Items",
            "4. Find Item by ID",
            "5. Update Item",
            "Press 'e' or 'Esc' to go back"
        };
        printMenuBox("ITEMS MANAGEMENT", options);
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
                default -> System.out.println("  ✘ Invalid choice. Please try again.");
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Database error: " + e.getMessage());
        }
    }

    // ── Input Helpers ─────────────────────────────────────────────────────────────
    public int getValidInt(Scanner scanner) {
        while (true) {
            try {
                int val = Integer.parseInt(scanner.nextLine().trim());
                if (val <= 0) System.out.println("  Must be a positive number.");
                else return val;
            } catch (NumberFormatException e) {
                System.out.println("  Error: Please enter a valid integer.");
            }
        }
    }

    public String getValidString(Scanner scanner) {
        while (true) {
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) System.out.println("  Error: Field cannot be empty.");
            else return input;
        }
    }

    // ── Add Item ──────────────────────────────────────────────────────────────────
    public void addItem(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("  Enter number of raw materials for this item: ");
        int length = getValidInt(scanner);
        int[] rawMaterialIds = getRawMaterialIds(scanner, length);

        if (!validateRawMaterials(connection, rawMaterialIds)) {
            System.out.println("  ✘ One or more invalid raw material IDs. Cannot add item.");
            return;
        }

        System.out.print("  Enter Item Name: ");
        name = getValidString(scanner);
        System.out.print("  Enter Price per Unit: ");
        pricePerUnit = getValidInt(scanner);

        insertItemIntoDatabase(connection);
        linkRawMaterialsToItem(connection, rawMaterialIds, itemId);
    }

    // ── Delete Item ───────────────────────────────────────────────────────────────
    public void deleteItems(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("  Enter Item ID to delete: ");
        int itemIdToDelete = getValidInt(scanner);

        String checkQuery = """
                SELECT EXISTS(
                    SELECT 1 FROM orders WHERE itemId = ?
                    UNION ALL
                    SELECT 1 FROM finish_goods WHERE itemId = ?
                )
                """;
        try (PreparedStatement cs = connection.prepareStatement(checkQuery)) {
            cs.setInt(1, itemIdToDelete);
            cs.setInt(2, itemIdToDelete);
            try (ResultSet rs = cs.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 1) {
                    System.out.println("  ✘ Item ID " + itemIdToDelete
                            + " is referenced by orders/finish goods. Cannot delete.");
                    return;
                }
            }
        }

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
                System.out.println(rows > 0
                        ? "  ✔ Item ID " + itemIdToDelete + " deleted successfully."
                        : "  ✘ No item found with ID " + itemIdToDelete);
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            System.out.println("  ✘ Error deleting item: " + e.getMessage());
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    // ── Find All ──────────────────────────────────────────────────────────────────
    public void findAll(Connection connection) throws SQLException {
        String query = """
                SELECT i.itemId, i.name, i.price_per_unit,
                       (SELECT COUNT(*) FROM items_raw_materials irm WHERE irm.itemId = i.itemId) AS raw_count,
                       i.createdAt
                FROM items i ORDER BY i.itemId
                """;

        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            printTopBorder(WIDTHS_LIST);
            printRow(HEADERS_LIST, WIDTHS_LIST, ALIGNS_LIST);
            printMidBorder(WIDTHS_LIST);

            boolean hasRows = false;
            while (rs.next()) {
                hasRows = true;
                printRow(new String[]{
                        String.valueOf(rs.getInt("itemId")),
                        rs.getString("name"),
                        String.valueOf(rs.getInt("price_per_unit")),
                        String.valueOf(rs.getInt("raw_count")),
                        rs.getString("createdAt")
                }, WIDTHS_LIST, ALIGNS_LIST);
            }
            if (!hasRows) printEmptyBox("  No items found.", 82);
            printBotBorder(WIDTHS_LIST);
        }
    }

    // ── Find By ID ────────────────────────────────────────────────────────────────
    public void findById(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("  Enter Item ID: ");
        int itemIdToFind = getValidInt(scanner);

        String itemQuery = "SELECT itemId, name, price_per_unit, createdAt FROM items WHERE itemId = ? LIMIT 1";
        String rmQuery = """
                SELECT rm.id, rm.name AS rm_name, rm.price, rm.quantity
                FROM items_raw_materials irm
                JOIN raw_materials rm ON irm.raw_material_id = rm.id
                WHERE irm.itemId = ?
                """;

        try (PreparedStatement stmt = connection.prepareStatement(itemQuery)) {
            stmt.setInt(1, itemIdToFind);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("  ✘ No item found with ID " + itemIdToFind);
                    return;
                }
                // Print item details header
                System.out.println("\n  ╔═══════════════════════════════════════════════╗");
                System.out.printf("  ║  Item #%-3d │ %-20s │ ₹%-8d ║%n",
                        rs.getInt("itemId"),
                        cell(rs.getString("name"), 20, Align.LEFT),
                        rs.getInt("price_per_unit"));
                System.out.printf("  ║  Created: %-36s║%n",
                        rs.getString("createdAt"));
                System.out.println("  ╚═══════════════════════════════════════════════╝");

                // Print raw materials sub-table
                System.out.println("  Linked Raw Materials:");
                printTopBorder(WIDTHS_RM);
                printRow(HEADERS_RM, WIDTHS_RM, ALIGNS_RM);
                printMidBorder(WIDTHS_RM);

                try (PreparedStatement rmStmt = connection.prepareStatement(rmQuery)) {
                    rmStmt.setInt(1, itemIdToFind);
                    try (ResultSet rmRs = rmStmt.executeQuery()) {
                        boolean anyRm = false;
                        while (rmRs.next()) {
                            anyRm = true;
                            printRow(new String[]{
                                    String.valueOf(rmRs.getInt("id")),
                                    rmRs.getString("rm_name"),
                                    String.valueOf(rmRs.getInt("price")),
                                    String.valueOf(rmRs.getInt("quantity"))
                            }, WIDTHS_RM, ALIGNS_RM);
                        }
                        if (!anyRm) printEmptyBox("  No raw materials linked.", 50);
                    }
                }
                printBotBorder(WIDTHS_RM);
            }
        }
    }

    // ── Update Item ───────────────────────────────────────────────────────────────
    public void updateItem(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("  Enter Item ID to update: ");
        int targetId = getValidInt(scanner);

        String checkQuery = "SELECT EXISTS(SELECT 1 FROM items WHERE itemId = ?)";
        try (PreparedStatement cs = connection.prepareStatement(checkQuery)) {
            cs.setInt(1, targetId);
            try (ResultSet rs = cs.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    System.out.println("  ✘ No item found with ID " + targetId);
                    return;
                }
            }
        }

        System.out.print("  Enter new Item Name: ");
        String newName = getValidString(scanner);
        System.out.print("  Enter new Price per Unit: ");
        int newPrice = getValidInt(scanner);

        String query = "UPDATE items SET name = ?, price_per_unit = ? WHERE itemId = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, newName);
            stmt.setInt(2, newPrice);
            stmt.setInt(3, targetId);
            int rows = stmt.executeUpdate();
            System.out.println(rows > 0
                    ? "  ✔ Item ID " + targetId + " updated successfully."
                    : "  ✘ Update failed. Item not found.");
        }
    }

    // ── Private Helpers ───────────────────────────────────────────────────────────
    private int[] getRawMaterialIds(Scanner scanner, int length) {
        int[] ids = new int[length];
        for (int i = 0; i < length; i++) {
            System.out.print("  Enter Raw Material " + (i + 1) + " ID: ");
            ids[i] = getValidInt(scanner);
        }
        return ids;
    }

    private boolean validateRawMaterials(Connection connection, int[] rawMaterialIds) throws SQLException {
        if (rawMaterialIds.length == 0) return true;
        StringBuilder placeholders = new StringBuilder("?");
        for (int i = 1; i < rawMaterialIds.length; i++) placeholders.append(",?");

        String query = "SELECT COUNT(*) FROM raw_materials WHERE id IN (" + placeholders + ")";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            for (int i = 0; i < rawMaterialIds.length; i++) stmt.setInt(i + 1, rawMaterialIds[i]);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) == rawMaterialIds.length;
            }
        }
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
                        System.out.println("  ✔ Item added successfully with ID: " + itemId);
                    }
                }
            } else {
                throw new SQLException("Item insert returned 0 rows.");
            }
        }
    }

    private void linkRawMaterialsToItem(Connection connection, int[] rawMaterialIds, int itemId) throws SQLException {
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
            System.out.println("  ✔ Raw materials linked to item successfully.");
        } catch (SQLException e) {
            connection.rollback();
            System.out.println("  ✘ Error linking raw materials: " + e.getMessage());
            throw e;
        } finally {
            connection.setAutoCommit(true);
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