package org.example.models;

import java.sql.*;
import java.util.Scanner;

import static org.example.models.TablePrinter.*;

public class Raw_Material implements EntityHandler {
    private static final int LOW_STOCK_THRESHOLD = 10;

    // Column configs
    private static final String[] HEADERS_ALL   = {"ID", "Name", "Supplier", "Price", "Qty", "Status"};
    private static final int[]    WIDTHS_ALL     = {5, 18, 20, 7, 7, 10};
    private static final Align[]  ALIGNS_ALL     = {Align.RIGHT, Align.LEFT, Align.LEFT,
                                                     Align.RIGHT, Align.RIGHT, Align.LEFT};

    private static final String[] HEADERS_SEARCH = {"ID", "Name", "Price", "Qty", "Status"};
    private static final int[]    WIDTHS_SEARCH  = {5, 22, 7, 7, 10};
    private static final Align[]  ALIGNS_SEARCH  = {Align.RIGHT, Align.LEFT, Align.RIGHT,
                                                     Align.RIGHT, Align.LEFT};

    private int id;
    private String name;
    private int quantity;
    private int supplierId;
    private int price;

    @Override
    public void showMenu() {
        String[] options = {
            "1. Add Raw Material",
            "2. Delete Raw Material",
            "3. View All Raw Materials",
            "4. Find Raw Material by ID",
            "5. Update Raw Material",
            "6. Search by Supplier",
            "Press 'e' or 'Esc' to go back"
        };
        printMenuBox("RAW MATERIAL MANAGEMENT", options);
    }

    @Override
    public void handleChoice(int choice, Connection connection, Scanner scanner) {
        try {
            switch (choice) {
                case 1 -> addRawMaterial(connection, scanner);
                case 2 -> deleteRawMaterial(connection, scanner);
                case 3 -> findAll(connection);
                case 4 -> findById(connection, scanner);
                case 5 -> updateRawMaterial(connection, scanner);
                case 6 -> searchBySupplier(connection, scanner);
                default -> System.out.println("  ✘ Invalid choice! Please try again.");
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

    // ── Add ───────────────────────────────────────────────────────────────────────
    public void addRawMaterial(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("  Enter Supplier ID: ");
        int supplierId = getValidInt(scanner);

        String checkSupplierQuery = "SELECT EXISTS(SELECT 1 FROM suppliers WHERE id = ?)";
        try (PreparedStatement cs = connection.prepareStatement(checkSupplierQuery)) {
            cs.setInt(1, supplierId);
            try (ResultSet rs = cs.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    System.out.println("  ✘ Supplier ID " + supplierId + " does not exist.");
                    return;
                }
            }
        }

        System.out.print("  Enter Name: ");
        String name = getValidString(scanner);
        System.out.print("  Enter Price: ");
        int price = getValidInt(scanner);
        System.out.print("  Enter Quantity: ");
        int quantity = getValidInt(scanner);

        String insertQuery = "INSERT INTO raw_materials (name, quantity, supplier_id, price) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
            stmt.setString(1, name);
            stmt.setInt(2, quantity);
            stmt.setInt(3, supplierId);
            stmt.setInt(4, price);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("  ✔ Raw material \"" + name + "\" added successfully.");
                if (quantity < LOW_STOCK_THRESHOLD)
                    System.out.println("  ⚠  Warning: Initial quantity is below low-stock threshold (" + LOW_STOCK_THRESHOLD + ").");
            } else {
                System.out.println("  ✘ Failed to add raw material.");
            }
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────────
    public void deleteRawMaterial(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("  Enter Raw Material ID to delete: ");
        int rawMaterialId = getValidInt(scanner);

        String checkQuery = "SELECT EXISTS(SELECT 1 FROM items_raw_materials WHERE raw_material_id = ?)";
        try (PreparedStatement cs = connection.prepareStatement(checkQuery)) {
            cs.setInt(1, rawMaterialId);
            try (ResultSet rs = cs.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 1) {
                    System.out.println("  ✘ Raw Material ID " + rawMaterialId
                            + " is linked to one or more items. Cannot delete.");
                    return;
                }
            }
        }

        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM raw_materials WHERE id = ?")) {
            stmt.setInt(1, rawMaterialId);
            int rows = stmt.executeUpdate();
            System.out.println(rows > 0
                    ? "  ✔ Raw material ID " + rawMaterialId + " deleted successfully."
                    : "  ✘ No raw material found with ID " + rawMaterialId);
        }
    }

    // ── Find All ──────────────────────────────────────────────────────────────────
    public void findAll(Connection connection) throws SQLException {
        String query = """
                SELECT rm.id, rm.name, s.name AS supplier_name,
                       rm.price, rm.quantity
                FROM raw_materials rm
                LEFT JOIN suppliers s ON rm.supplier_id = s.id
                ORDER BY rm.id
                """;

        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            printTopBorder(WIDTHS_ALL);
            printRow(HEADERS_ALL, WIDTHS_ALL, ALIGNS_ALL);
            printMidBorder(WIDTHS_ALL);

            boolean hasRows = false;
            while (rs.next()) {
                hasRows = true;
                int qty = rs.getInt("quantity");
                String status = qty < LOW_STOCK_THRESHOLD ? "⚠ LOW STOCK" : "✔ OK";
                printRow(new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("name"),
                        rs.getString("supplier_name"),
                        String.valueOf(rs.getInt("price")),
                        String.valueOf(qty),
                        status
                }, WIDTHS_ALL, ALIGNS_ALL);
            }
            if (!hasRows) printEmptyBox("  No raw materials found.", 78);
            printBotBorder(WIDTHS_ALL);
        }
    }

    // ── Find By ID ────────────────────────────────────────────────────────────────
    public void findById(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("  Enter Raw Material ID: ");
        int targetId = getValidInt(scanner);

        String query = """
                SELECT rm.id, rm.name, s.name AS supplier_name,
                       rm.price, rm.quantity
                FROM raw_materials rm
                LEFT JOIN suppliers s ON rm.supplier_id = s.id
                WHERE rm.id = ? LIMIT 1
                """;

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, targetId);
            try (ResultSet rs = stmt.executeQuery()) {
                printTopBorder(WIDTHS_ALL);
                printRow(HEADERS_ALL, WIDTHS_ALL, ALIGNS_ALL);
                printMidBorder(WIDTHS_ALL);
                if (rs.next()) {
                    int qty = rs.getInt("quantity");
                    printRow(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("name"),
                            rs.getString("supplier_name"),
                            String.valueOf(rs.getInt("price")),
                            String.valueOf(qty),
                            qty < LOW_STOCK_THRESHOLD ? "⚠ LOW STOCK" : "✔ OK"
                    }, WIDTHS_ALL, ALIGNS_ALL);
                } else {
                    printEmptyBox("  No raw material found with ID " + targetId, 78);
                }
                printBotBorder(WIDTHS_ALL);
            }
        }
    }

    // ── Update ────────────────────────────────────────────────────────────────────
    public void updateRawMaterial(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("  Enter Raw Material ID to update: ");
        int materialId = getValidInt(scanner);

        String selectQuery = "SELECT price, quantity FROM raw_materials WHERE id = ? LIMIT 1";
        int currentQty, currentPrice;
        try (PreparedStatement sel = connection.prepareStatement(selectQuery)) {
            sel.setInt(1, materialId);
            try (ResultSet rs = sel.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("  ✘ Raw material ID " + materialId + " not found.");
                    return;
                }
                currentQty   = rs.getInt("quantity");
                currentPrice = rs.getInt("price");
            }
        }

        System.out.println("  Current → Price: " + currentPrice + "  |  Quantity: " + currentQty);
        System.out.print("  Enter new Price: ");
        int newPrice = getValidInt(scanner);
        System.out.print("  Enter quantity to ADD to current stock: ");
        int added = getValidInt(scanner);
        int updatedQty = Math.max(0, currentQty + added);

        String updateQuery = "UPDATE raw_materials SET price = ?, quantity = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(updateQuery)) {
            stmt.setInt(1, newPrice);
            stmt.setInt(2, updatedQty);
            stmt.setInt(3, materialId);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("  ✔ Updated. New quantity: " + updatedQty);
                if (updatedQty < LOW_STOCK_THRESHOLD)
                    System.out.println("  ⚠  Warning: Still below low-stock threshold (" + LOW_STOCK_THRESHOLD + ").");
            } else {
                System.out.println("  ✘ Update failed.");
            }
        }
    }

    // ── Search By Supplier ────────────────────────────────────────────────────────
    public void searchBySupplier(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("  Enter Supplier ID: ");
        int targetSupplierId = getValidInt(scanner);

        String checkQuery = "SELECT EXISTS(SELECT 1 FROM suppliers WHERE id = ?)";
        try (PreparedStatement cs = connection.prepareStatement(checkQuery)) {
            cs.setInt(1, targetSupplierId);
            try (ResultSet rs = cs.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    System.out.println("  ✘ No supplier found with ID " + targetSupplierId);
                    return;
                }
            }
        }

        String query = """
                SELECT rm.id, rm.name, rm.price, rm.quantity
                FROM raw_materials rm
                WHERE rm.supplier_id = ?
                ORDER BY rm.id
                """;

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, targetSupplierId);
            try (ResultSet rs = stmt.executeQuery()) {
                printTopBorder(WIDTHS_SEARCH);
                printRow(HEADERS_SEARCH, WIDTHS_SEARCH, ALIGNS_SEARCH);
                printMidBorder(WIDTHS_SEARCH);

                boolean hasRows = false;
                while (rs.next()) {
                    hasRows = true;
                    int qty = rs.getInt("quantity");
                    printRow(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("name"),
                            String.valueOf(rs.getInt("price")),
                            String.valueOf(qty),
                            qty < LOW_STOCK_THRESHOLD ? "⚠ LOW STOCK" : "✔ OK"
                    }, WIDTHS_SEARCH, ALIGNS_SEARCH);
                }
                if (!hasRows)
                    printEmptyBox("  No raw materials found for supplier ID " + targetSupplierId, 58);
                printBotBorder(WIDTHS_SEARCH);
            }
        }
    }

    // ── Menu Helper ───────────────────────────────────────────────────────────────
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
