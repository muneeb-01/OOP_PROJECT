package org.example.models;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

public class Order implements EntityHandler {
    private String name;
    private int receiverID;
    private int itemId;
    private int quantity;
    private int isFullFilled = 0;
    private int id;

    @Override
    public void showMenu() {
        System.out.println("\nOrder Management Menu:");
        System.out.println("1. Add Order");
        System.out.println("2. Delete Order");
        System.out.println("3. Display All Orders");
        System.out.println("4. Find Order by ID");
        System.out.println("5. Mark Order as Completed");
        System.out.println("6. Update Order Quantity");
        System.out.println("Press 'e' or 'Esc' to go back");
    }

    @Override
    public void handleChoice(int choice, Connection connection, Scanner scanner) {
        switch (choice) {
            case 1 -> addOrder(connection, scanner);
            case 2 -> deleteOrder(connection, scanner);
            case 3 -> displayOrders(connection);
            case 4 -> findById(connection, scanner);
            case 5 -> completion(connection, scanner);
            case 6 -> updateOrderQuantity(connection, scanner);
            default -> System.out.println("Invalid choice. Please try again.");
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

    public int getIsFullFilled(Scanner scanner) {
        while (true) {
            try {
                int val = Integer.parseInt(scanner.nextLine().trim());
                if (val < 0 || val > 1) {
                    System.out.println("Error: Enter 1 for fulfilled or 0 for pending.");
                } else {
                    return val;
                }
            } catch (NumberFormatException e) {
                System.out.println("Error: Enter 1 for true or 0 for false.");
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

    // ── Add Order ─────────────────────────────────────────────────────────────────
    public void addOrder(Connection connection, Scanner scanner) {
        try {
            System.out.print("Enter Order Name: ");
            this.name = getValidString(scanner);

            System.out.print("Enter Item ID: ");
            this.itemId = getValidInt(scanner);
            if (!existsInTable(connection, "items", "itemId", this.itemId)) {
                System.out.println("Item not found. Please enter a valid Item ID.");
                return;
            }

            System.out.print("Enter Receiver ID: ");
            this.receiverID = getValidInt(scanner);
            if (!existsInTable(connection, "receiver", "id", this.receiverID)) {
                System.out.println("Receiver not found. Please enter a valid Receiver ID.");
                return;
            }

            System.out.print("Enter Quantity: ");
            this.quantity = getValidInt(scanner);

            // Check and deduct raw materials within a transaction
            if (!deductRawMaterialsForOrder(connection, this.itemId, this.quantity)) {
                System.out.println("Insufficient raw materials. Order not placed.");
                return;
            }

            System.out.print("Is the order fulfilled? (0 = No, 1 = Yes): ");
            this.isFullFilled = getIsFullFilled(scanner);

            String insertOrderQuery = """
                INSERT INTO orders (name, itemId, receiverId, fulfilled, quantity)
                VALUES (?, ?, ?, ?, ?)
                """;

            connection.setAutoCommit(false);
            try (PreparedStatement pstmt = connection.prepareStatement(
                    insertOrderQuery, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, this.name);
                pstmt.setInt(2, this.itemId);
                pstmt.setInt(3, this.receiverID);
                pstmt.setInt(4, this.isFullFilled);
                pstmt.setInt(5, this.quantity);

                int rows = pstmt.executeUpdate();
                if (rows > 0) {
                    try (ResultSet keys = pstmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            this.id = keys.getInt(1);
                        }
                    }
                    System.out.println("Order added successfully! Order ID: " + this.id);

                    // If already marked fulfilled, move to finish_goods immediately
                    if (this.isFullFilled == 1) {
                        moveToFinishGoods(connection, this.id,
                                this.name, this.itemId, this.receiverID, this.quantity);
                    }
                    connection.commit();
                } else {
                    connection.rollback();
                    System.out.println("Failed to add the order.");
                }
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.out.println("Error adding the order: " + e.getMessage());
        }
    }

    // ── Delete Order ──────────────────────────────────────────────────────────────
    public void deleteOrder(Connection connection, Scanner scanner) {
        System.out.print("Enter Order ID to delete: ");
        int orderID = getValidInt(scanner);   // fixed: was scanner.nextInt()

        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM orders WHERE orderId = ?")) {
            stmt.setInt(1, orderID);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("Order ID " + orderID + " deleted successfully.");
            } else {
                System.out.println("No order found with ID " + orderID);
            }
        } catch (SQLException e) {
            System.out.println("Error deleting order: " + e.getMessage());
        }
    }

    // ── Display All Orders ────────────────────────────────────────────────────────
    public void displayOrders(Connection connection) {
        // JOIN with receiver and item names for more readable output
        String query = """
                SELECT o.orderId, o.name, o.quantity, o.itemId,
                       i.name AS item_name, o.receiverId,
                       r.name AS receiver_name, o.fulfilled, o.createdAt
                FROM orders o
                LEFT JOIN items i ON o.itemId = i.itemId
                LEFT JOIN receiver r ON o.receiverId = r.id
                ORDER BY o.orderId
                """;

        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            System.out.printf("%n%-8s  %-12s  %-8s  %-15s  %-15s  %-10s  %s%n",
                    "OrderID", "Name", "Qty", "Item", "Receiver", "Fulfilled", "CreatedAt");
            System.out.println("-".repeat(90));

            boolean hasRows = false;
            while (rs.next()) {
                hasRows = true;
                System.out.printf("%-8d  %-12s  %-8d  %-15s  %-15s  %-10s  %s%n",
                        rs.getInt("orderId"),
                        rs.getString("name"),
                        rs.getInt("quantity"),
                        rs.getString("item_name"),
                        rs.getString("receiver_name"),
                        rs.getInt("fulfilled") == 1 ? "Yes" : "No",
                        rs.getString("createdAt"));
            }
            if (!hasRows) System.out.println("No orders found.");

        } catch (SQLException e) {
            System.out.println("Error fetching orders: " + e.getMessage());
        }
    }

    // ── Find By ID ────────────────────────────────────────────────────────────────
    public void findById(Connection connection, Scanner scanner) {
        System.out.print("Enter Order ID: ");
        int targetId = getValidInt(scanner);   // fixed: was scanner.nextInt()

        String query = """
                SELECT o.orderId, o.name, o.quantity, o.itemId,
                       i.name AS item_name, o.receiverId,
                       r.name AS receiver_name, o.fulfilled, o.createdAt
                FROM orders o
                LEFT JOIN items i ON o.itemId = i.itemId
                LEFT JOIN receiver r ON o.receiverId = r.id
                WHERE o.orderId = ?
                LIMIT 1
                """;

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, targetId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    System.out.printf("%n%-8s  %-12s  %-8s  %-15s  %-15s  %-10s  %s%n",
                            "OrderID", "Name", "Qty", "Item", "Receiver", "Fulfilled", "CreatedAt");
                    System.out.println("-".repeat(90));
                    System.out.printf("%-8d  %-12s  %-8d  %-15s  %-15s  %-10s  %s%n",
                            rs.getInt("orderId"),
                            rs.getString("name"),
                            rs.getInt("quantity"),
                            rs.getString("item_name"),
                            rs.getString("receiver_name"),
                            rs.getInt("fulfilled") == 1 ? "Yes" : "No",
                            rs.getString("createdAt"));
                } else {
                    System.out.println("No order found with ID " + targetId);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error finding order: " + e.getMessage());
        }
    }

    // ── Mark Order as Completed ───────────────────────────────────────────────────
    public void completion(Connection connection, Scanner scanner) {
        System.out.print("Enter Order ID to mark as completed: ");
        int targetId = getValidInt(scanner);   // fixed: was scanner.nextInt()

        // Fetch order data from DB (bug fix: was using stale instance fields)
        String selectQuery = """
                SELECT orderId, name, itemId, receiverId, quantity
                FROM orders WHERE orderId = ? LIMIT 1
                """;

        try (PreparedStatement stmt = connection.prepareStatement(selectQuery)) {
            stmt.setInt(1, targetId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("No order found with ID " + targetId);
                    return;
                }
                int orderId    = rs.getInt("orderId");
                String oName   = rs.getString("name");
                int oItemId    = rs.getInt("itemId");
                int oReceiverId = rs.getInt("receiverId");
                int oQuantity  = rs.getInt("quantity");

                connection.setAutoCommit(false);
                try {
                    moveToFinishGoods(connection, orderId, oName, oItemId, oReceiverId, oQuantity);
                    connection.commit();
                    System.out.println("Order ID " + orderId + " marked as completed and moved to Finish Goods.");
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error completing order: " + e.getMessage());
        }
    }

    // ── Update Order Quantity ─────────────────────────────────────────────────────
    public void updateOrderQuantity(Connection connection, Scanner scanner) {
        System.out.print("Enter Order ID: ");
        int orderId = getValidInt(scanner);   // fixed: was scanner.nextInt()

        System.out.print("Enter New Quantity: ");
        int newQuantity = getValidInt(scanner);   // fixed: was scanner.nextInt()

        try {
            String selectQuery = "SELECT quantity, itemId FROM orders WHERE orderId = ? LIMIT 1";
            int currentQuantity;
            int currentItemId;

            try (PreparedStatement pstmt = connection.prepareStatement(selectQuery)) {
                pstmt.setInt(1, orderId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("Order not found with ID " + orderId);
                        return;
                    }
                    currentQuantity = rs.getInt("quantity");
                    currentItemId   = rs.getInt("itemId");
                }
            }

            int additionalQuantity = newQuantity - currentQuantity;
            if (additionalQuantity > 0) {
                // Only deduct extra raw materials needed for the increased quantity
                if (!deductRawMaterialsForOrder(connection, currentItemId, additionalQuantity)) {
                    System.out.println("Insufficient raw materials for the quantity increase.");
                    return;
                }
            }

            String updateQuery = "UPDATE orders SET quantity = ? WHERE orderId = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(updateQuery)) {
                pstmt.setInt(1, newQuantity);
                pstmt.setInt(2, orderId);
                int rows = pstmt.executeUpdate();
                if (rows > 0) {
                    System.out.println("Order ID " + orderId + " quantity updated to " + newQuantity);
                } else {
                    System.out.println("Failed to update order quantity.");
                }
            }

        } catch (SQLException e) {
            System.out.println("Error updating order quantity: " + e.getMessage());
        }
    }

    // ── Private Helpers ───────────────────────────────────────────────────────────

    /**
     * Moves an order to finish_goods and deletes it from orders.
     * Must be called within a transaction.
     */
    private void moveToFinishGoods(Connection connection,
                                   int orderId, String oName, int oItemId,
                                   int oReceiverId, int oQuantity) throws SQLException {
        String insertFG = """
                INSERT INTO finish_goods (name, itemId, receiverId, fulfilled, quantity)
                VALUES (?, ?, ?, 1, ?)
                """;
        try (PreparedStatement pstmt = connection.prepareStatement(insertFG)) {
            pstmt.setString(1, oName);
            pstmt.setInt(2, oItemId);
            pstmt.setInt(3, oReceiverId);
            pstmt.setInt(4, oQuantity);
            pstmt.executeUpdate();
        }

        try (PreparedStatement pstmt = connection.prepareStatement(
                "DELETE FROM orders WHERE orderId = ?")) {
            pstmt.setInt(1, orderId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Checks raw material availability for all raw materials linked to an item,
     * then deducts the required quantity using a single JOIN query.
     * Returns false if any raw material is insufficient.
     */
    private boolean deductRawMaterialsForOrder(Connection connection,
                                               int itemId, int quantity) throws SQLException {
        // Fetch all linked raw materials in one JOIN query
        String fetchQuery = """
                SELECT rm.id, rm.name, rm.quantity AS available
                FROM items_raw_materials irm
                JOIN raw_materials rm ON irm.raw_material_id = rm.id
                WHERE irm.itemId = ?
                """;

        try (PreparedStatement stmt = connection.prepareStatement(fetchQuery)) {
            stmt.setInt(1, itemId);
            try (ResultSet rs = stmt.executeQuery()) {
                // Collect insufficiency first before any updates
                java.util.List<int[]> toDeduct = new java.util.ArrayList<>();
                while (rs.next()) {
                    int rmId    = rs.getInt("id");
                    int avail   = rs.getInt("available");
                    String rmName = rs.getString("name");
                    if (avail < quantity) {
                        System.out.printf("Insufficient '%s' (ID %d). Required: %d, Available: %d%n",
                                rmName, rmId, quantity, avail);
                        return false;
                    }
                    toDeduct.add(new int[]{rmId});
                }

                // All materials available – deduct in batch
                String deductQuery = "UPDATE raw_materials SET quantity = quantity - ? WHERE id = ?";
                try (PreparedStatement deductStmt = connection.prepareStatement(deductQuery)) {
                    for (int[] rm : toDeduct) {
                        deductStmt.setInt(1, quantity);
                        deductStmt.setInt(2, rm[0]);
                        deductStmt.addBatch();
                    }
                    deductStmt.executeBatch();
                }
            }
        }
        return true;
    }

    /**
     * Optimized existence check using SQL EXISTS.
     */
    private boolean existsInTable(Connection connection, String table,
                                  String column, int value) throws SQLException {
        String query = "SELECT EXISTS(SELECT 1 FROM " + table + " WHERE " + column + " = ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, value);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }
}
