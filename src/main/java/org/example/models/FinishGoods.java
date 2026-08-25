package org.example.models;

import java.sql.*;
import java.util.Scanner;

import static org.example.models.TablePrinter.*;

public class FinishGoods implements EntityHandler {
    private int itemId;
    private String name;
    private int quantity;
    private int receiverID;
    private int id;
    private int isFullFilled;

    // Column configs
    private static final String[] HEADERS = {"FG ID", "Name", "Qty", "Item", "Receiver", "Delivered", "Created At"};
    private static final int[]    WIDTHS  = {7, 14, 5, 16, 16, 11, 22};
    private static final Align[]  ALIGNS  = {Align.RIGHT, Align.LEFT, Align.RIGHT,
                                              Align.LEFT, Align.LEFT, Align.LEFT, Align.LEFT};

    @Override
    public void showMenu() {
        printMenuBox("FINISH GOODS MANAGEMENT", new String[]{
            "1. Display All Finish Goods",
            "2. Find Finish Good by ID",
            "3. Show Delivered Items",
            "4. Show Pending (Undelivered) Items",
            "5. Mark as Delivered",
            "Press 'e' or 'Esc' to go back"
        });
    }

    @Override
    public void handleChoice(int choice, Connection connection, Scanner scanner) {
        switch (choice) {
            case 1 -> displayOrders(connection);
            case 2 -> findById(connection, scanner);
            case 3 -> showDeliveredItems(connection);
            case 4 -> showPendingItems(connection);
            case 5 -> markAsDelivered(connection, scanner);
            default -> System.out.println("  ✘ Invalid choice. Please try again.");
        }
    }

    // ── Input Helper ──────────────────────────────────────────────────────────────
    public int getValidInt(Scanner scanner) {
        while (true) {
            try {
                int val = Integer.parseInt(scanner.nextLine().trim());
                if (val <= 0) {
                    System.out.println("  Must be a positive number.");
                } else {
                    return val;
                }
            } catch (NumberFormatException e) {
                System.out.println("  Error: Invalid input. Please enter a valid integer.");
            }
        }
    }

    // ── Display All Finish Goods ──────────────────────────────────────────────────
    public void displayOrders(Connection connection) {
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
        System.out.print("  Enter Finish Good ID: ");
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
                printTopBorder(WIDTHS);
                printRow(HEADERS, WIDTHS, ALIGNS);
                printMidBorder(WIDTHS);

                if (rs.next()) {
                    String status = rs.getInt("fulfilled") == 1 ? "✔ Yes" : "⏳ Pending";
                    printRow(new String[]{
                            String.valueOf(rs.getInt("orderId")),
                            rs.getString("name"),
                            String.valueOf(rs.getInt("quantity")),
                            rs.getString("item_name"),
                            rs.getString("receiver_name"),
                            status,
                            rs.getString("createdAt")
                    }, WIDTHS, ALIGNS);
                } else {
                    printEmptyBox("  No finish good found with ID " + targetId, 99);
                }
                printBotBorder(WIDTHS);
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Error finding finish good: " + e.getMessage());
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
        System.out.print("  Enter Finish Good ID to mark as delivered: ");
        int targetId = getValidInt(scanner);

        String checkQuery = "SELECT fulfilled FROM finish_goods WHERE orderId = ? LIMIT 1";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
            checkStmt.setInt(1, targetId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("  ✘ No finish good found with ID " + targetId);
                    return;
                }
                if (rs.getInt("fulfilled") == 1) {
                    System.out.println("  ✔ Finish Good ID " + targetId + " is already marked as delivered.");
                    return;
                }
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Error checking delivery status: " + e.getMessage());
            return;
        }

        String updateQuery = "UPDATE finish_goods SET fulfilled = 1 WHERE orderId = ?";
        try (PreparedStatement stmt = connection.prepareStatement(updateQuery)) {
            stmt.setInt(1, targetId);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("  ✔ Finish Good ID " + targetId + " marked as delivered.");
            } else {
                System.out.println("  ✘ Update failed.");
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Error updating delivery status: " + e.getMessage());
        }
    }

    // ── Display Helpers ───────────────────────────────────────────────────────────
    private void printFinishGoodsResult(Connection connection, String query, int[] params) {
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setInt(i + 1, params[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                printTopBorder(WIDTHS);
                printRow(HEADERS, WIDTHS, ALIGNS);
                printMidBorder(WIDTHS);

                boolean hasRows = false;
                while (rs.next()) {
                    hasRows = true;
                    String status = rs.getInt("fulfilled") == 1 ? "✔ Yes" : "⏳ Pending";
                    printRow(new String[]{
                            String.valueOf(rs.getInt("orderId")),
                            rs.getString("name"),
                            String.valueOf(rs.getInt("quantity")),
                            rs.getString("item_name"),
                            rs.getString("receiver_name"),
                            status,
                            rs.getString("createdAt")
                    }, WIDTHS, ALIGNS);
                }
                if (!hasRows) printEmptyBox("  No records found.", 99);
                printBotBorder(WIDTHS);
            }
        } catch (SQLException e) {
            System.out.println("  ✘ Error fetching finish goods: " + e.getMessage());
        }
    }

    private static void printMenuBox(String title, String[] options) {
        int w = 44;
        System.out.println("\n╔" + "═".repeat(w) + "╗");
        System.out.printf("║  %-" + (w - 2) + "s║%n", title);
        System.out.println("╠" + "═".repeat(w) + "╣");
        for (String opt : options) {
            System.out.printf("║  %-" + (w - 2) + "s║%n", opt);
        }
        System.out.println("╚" + "═".repeat(w) + "╝");
    }
}
