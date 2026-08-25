package org.example.models;

import java.sql.*;
import java.util.Scanner;

public class FinishGoods implements EntityHandler {
    private int itemId;
    private String name;
    private int quantity;
    private int receiverID;
    private int id;
    private int isFullFilled;

    @Override
    public void showMenu() {
        System.out.println("\nFinish Goods Management Menu:");
        System.out.println("1. Display All Finish Goods");
        System.out.println("2. Find Finish Good by ID");
        System.out.println("3. Show Delivered Items");
        System.out.println("4. Show Pending (Undelivered) Items");
        System.out.println("5. Mark as Delivered");
        System.out.println("Press 'e' or 'Esc' to go back");
    }

    @Override
    public void handleChoice(int choice, Connection connection, Scanner scanner) {
        switch (choice) {
            case 1 -> displayOrders(connection);
            case 2 -> findById(connection, scanner);
            case 3 -> showDeliveredItems(connection);
            case 4 -> showPendingItems(connection);
            case 5 -> markAsDelivered(connection, scanner);
            default -> System.out.println("Invalid choice. Please try again.");
        }
    }

    // ── Input Helper ──────────────────────────────────────────────────────────────
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

    // ── Display All Finish Goods ──────────────────────────────────────────────────
    public void displayOrders(Connection connection) {
        // JOIN with items and receiver for human-readable output
        String query = """
                SELECT fg.orderId, fg.name, fg.quantity,
                       i.name AS item_name, fg.itemId,
                       r.name AS receiver_name, fg.receiverId,
                       fg.fulfilled, fg.createdAt
                FROM finish_goods fg
                LEFT JOIN items i ON fg.itemId = i.itemId
                LEFT JOIN receiver r ON fg.receiverId = r.id
                ORDER BY fg.orderId
                """;
        printFinishGoodsResult(connection, query, new int[]{});
    }

    // ── Find By ID ────────────────────────────────────────────────────────────────
    public void findById(Connection connection, Scanner scanner) {
        System.out.print("Enter Finish Good ID: ");
        int targetId = getValidInt(scanner);

        String query = """
                SELECT fg.orderId, fg.name, fg.quantity,
                       i.name AS item_name, fg.itemId,
                       r.name AS receiver_name, fg.receiverId,
                       fg.fulfilled, fg.createdAt
                FROM finish_goods fg
                LEFT JOIN items i ON fg.itemId = i.itemId
                LEFT JOIN receiver r ON fg.receiverId = r.id
                WHERE fg.orderId = ?
                LIMIT 1
                """;

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, targetId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    printHeader();
                    printRow(rs);
                } else {
                    System.out.println("No finish good found with ID " + targetId);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error finding finish good: " + e.getMessage());
        }
    }

    // ── Show Delivered Items (fulfilled = 1) ──────────────────────────────────────
    public void showDeliveredItems(Connection connection) {
        String query = """
                SELECT fg.orderId, fg.name, fg.quantity,
                       i.name AS item_name, fg.itemId,
                       r.name AS receiver_name, fg.receiverId,
                       fg.fulfilled, fg.createdAt
                FROM finish_goods fg
                LEFT JOIN items i ON fg.itemId = i.itemId
                LEFT JOIN receiver r ON fg.receiverId = r.id
                WHERE fg.fulfilled = 1
                ORDER BY fg.orderId
                """;
        printFinishGoodsResult(connection, query, new int[]{});
    }

    // ── Show Pending / Undelivered Items (fulfilled = 0) ──────────────────────────
    public void showPendingItems(Connection connection) {
        String query = """
                SELECT fg.orderId, fg.name, fg.quantity,
                       i.name AS item_name, fg.itemId,
                       r.name AS receiver_name, fg.receiverId,
                       fg.fulfilled, fg.createdAt
                FROM finish_goods fg
                LEFT JOIN items i ON fg.itemId = i.itemId
                LEFT JOIN receiver r ON fg.receiverId = r.id
                WHERE fg.fulfilled = 0
                ORDER BY fg.orderId
                """;
        printFinishGoodsResult(connection, query, new int[]{});
    }

    // ── Mark as Delivered ─────────────────────────────────────────────────────────
    public void markAsDelivered(Connection connection, Scanner scanner) {
        System.out.print("Enter Finish Good ID to mark as delivered: ");
        int targetId = getValidInt(scanner);

        // Check if already delivered
        String checkQuery = "SELECT fulfilled FROM finish_goods WHERE orderId = ? LIMIT 1";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
            checkStmt.setInt(1, targetId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("No finish good found with ID " + targetId);
                    return;
                }
                if (rs.getInt("fulfilled") == 1) {
                    System.out.println("Finish Good ID " + targetId + " is already marked as delivered.");
                    return;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error checking delivery status: " + e.getMessage());
            return;
        }

        String updateQuery = "UPDATE finish_goods SET fulfilled = 1 WHERE orderId = ?";
        try (PreparedStatement stmt = connection.prepareStatement(updateQuery)) {
            stmt.setInt(1, targetId);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("Finish Good ID " + targetId + " marked as delivered.");
            } else {
                System.out.println("Update failed.");
            }
        } catch (SQLException e) {
            System.out.println("Error updating delivery status: " + e.getMessage());
        }
    }

    // ── Display Helpers ───────────────────────────────────────────────────────────
    private void printFinishGoodsResult(Connection connection, String query, int[] params) {
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setInt(i + 1, params[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                printHeader();
                boolean hasRows = false;
                while (rs.next()) {
                    hasRows = true;
                    printRow(rs);
                }
                if (!hasRows) System.out.println("No records found.");
            }
        } catch (SQLException e) {
            System.out.println("Error fetching finish goods: " + e.getMessage());
        }
    }

    private void printHeader() {
        System.out.printf("%n%-8s  %-12s  %-8s  %-15s  %-15s  %-10s  %s%n",
                "ID", "Name", "Qty", "Item", "Receiver", "Delivered", "CreatedAt");
        System.out.println("-".repeat(90));
    }

    private void printRow(ResultSet rs) throws SQLException {
        System.out.printf("%-8d  %-12s  %-8d  %-15s  %-15s  %-10s  %s%n",
                rs.getInt("orderId"),
                rs.getString("name"),
                rs.getInt("quantity"),
                rs.getString("item_name"),
                rs.getString("receiver_name"),
                rs.getInt("fulfilled") == 1 ? "Yes" : "No",
                rs.getString("createdAt"));
    }
}
