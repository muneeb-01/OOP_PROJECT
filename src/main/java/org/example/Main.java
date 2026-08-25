package org.example;

import org.example.models.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

import static org.example.models.TablePrinter.*;

public class Main {
    public static void main(String[] args) {
        ConnectDB connectDB = new ConnectDB();
        Connection connection = connectDB.getConnection();
        if (connection == null) {
            System.err.println("Could not connect to the database. Exiting.");
            return;
        }
        createTablesIfNotExist(connection);
        createIndexesIfNotExist(connection);
        runMainMenu(connection);
    }

    // ── Main Menu ─────────────────────────────────────────────────────────────────
    static void runMainMenu(Connection connection) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            showMainMenu();
            int choice = getUserInput(scanner);
            if (choice == -1) {
                System.out.println("Exiting Supply Chain Management System. Goodbye!");
                break;
            }
            processMainMenuChoice(choice, connection, scanner);
        }
    }

    static void showMainMenu() {
        int w = 48;
        System.out.println("\n╔" + "═".repeat(w) + "╗");
        System.out.printf("║  %-44s  ║%n", "SUPPLY CHAIN MANAGEMENT SYSTEM");
        System.out.println("╠" + "═".repeat(w) + "╣");
        System.out.printf("║  %-44s  ║%n", "1. Supplier Management");
        System.out.printf("║  %-44s  ║%n", "2. Receiver Management");
        System.out.printf("║  %-44s  ║%n", "3. Raw Material Management");
        System.out.printf("║  %-44s  ║%n", "4. Item Management");
        System.out.printf("║  %-44s  ║%n", "5. Order Management");
        System.out.printf("║  %-44s  ║%n", "6. Finish Goods Management");
        System.out.printf("║  %-44s  ║%n", "7. Executive Dashboard");
        System.out.printf("║  %-44s  ║%n", "Press 'e' or 'Esc' to Exit");
        System.out.println("╚" + "═".repeat(w) + "╝");
    }

    static void processMainMenuChoice(int choice, Connection connection, Scanner scanner) {
        if (choice == 7) {
            showDashboard(connection);
            return;
        }

        EntityHandler entityHandler = switch (choice) {
            case 1 -> new Supplier();
            case 2 -> new Receiver();
            case 3 -> new Raw_Material();
            case 4 -> new Items();
            case 5 -> new Order();
            case 6 -> new FinishGoods();
            default -> null;
        };

        if (entityHandler != null) {
            runEntityMenu(entityHandler, connection, scanner);
        } else {
            System.out.println("  ✘ Invalid choice. Please try again.");
        }
    }

    static void runEntityMenu(EntityHandler entityHandler, Connection connection, Scanner scanner) {
        while (true) {
            entityHandler.showMenu();
            int choice = getUserInput(scanner);
            if (choice == -1) {
                System.out.println("  ← Going back to main menu...");
                break;
            }
            entityHandler.handleChoice(choice, connection, scanner);
        }
    }

    static int getUserInput(Scanner scanner) {
        System.out.print("\nEnter choice (or 'e'/'Esc' to exit): ");
        String input = scanner.nextLine().trim();

        if (input.equalsIgnoreCase("e") || input.equalsIgnoreCase("esc")) {
            return -1;
        }
        if (input.isEmpty()) {
            System.out.println("  Input cannot be empty. Please enter a valid number.");
            return 0;
        }
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            System.out.println("  Invalid input. Please enter a valid number.");
            return 0;
        }
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────────
    static void showDashboard(Connection connection) {
        String dashboardQuery = """
                SELECT
                    (SELECT COUNT(*) FROM suppliers)                         AS total_suppliers,
                    (SELECT COUNT(*) FROM receiver)                          AS total_receivers,
                    (SELECT COUNT(*) FROM raw_materials)                     AS total_raw_materials,
                    (SELECT COUNT(*) FROM raw_materials WHERE quantity < 10) AS low_stock_count,
                    (SELECT COUNT(*) FROM items)                             AS total_items,
                    (SELECT COUNT(*) FROM orders)                            AS pending_orders,
                    (SELECT COUNT(*) FROM finish_goods)                      AS total_finish_goods,
                    (SELECT COUNT(*) FROM finish_goods WHERE fulfilled = 1)   AS delivered_count,
                    (SELECT COUNT(*) FROM finish_goods WHERE fulfilled = 0)   AS undelivered_count
                """;

        try (PreparedStatement stmt = connection.prepareStatement(dashboardQuery);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                int w = 52;
                System.out.println("\n╔" + "═".repeat(w) + "╗");
                System.out.printf("║  %-48s  ║%n", "EXECUTIVE DASHBOARD & SYSTEM METRICS");
                System.out.println("╠" + "═".repeat(w) + "╣");

                printDashRow("Suppliers Registered", String.valueOf(rs.getInt("total_suppliers")), w - 4);
                printDashRow("Receivers Registered", String.valueOf(rs.getInt("total_receivers")), w - 4);
                printDivider(w - 4);

                printDashRow("Total Raw Material Types", String.valueOf(rs.getInt("total_raw_materials")), w - 4);
                int lowStock = rs.getInt("low_stock_count");
                String lowStockVal = String.valueOf(lowStock) + (lowStock > 0 ? " ⚠ ALERT" : " ✔ OK");
                printDashRow("Low-Stock Raw Materials (<10)", lowStockVal, w - 4);
                printDivider(w - 4);

                printDashRow("Catalog Items", String.valueOf(rs.getInt("total_items")), w - 4);
                printDivider(w - 4);

                printDashRow("Active Pending Orders", String.valueOf(rs.getInt("pending_orders")), w - 4);
                printDivider(w - 4);

                printDashRow("Total Finished Goods", String.valueOf(rs.getInt("total_finish_goods")), w - 4);
                printDashRow("  ├─ Delivered to Receiver", String.valueOf(rs.getInt("delivered_count")), w - 4);
                printDashRow("  └─ Pending Delivery", String.valueOf(rs.getInt("undelivered_count")), w - 4);

                System.out.println("╚" + "═".repeat(w) + "╝");
            }

        } catch (SQLException e) {
            System.out.println("  ✘ Error fetching dashboard data: " + e.getMessage());
        }
    }

    // ── Table Setup ───────────────────────────────────────────────────────────────
    static void createTablesIfNotExist(Connection connection) {
        try (Statement stmt = connection.createStatement()) {

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS suppliers (
                        id      INTEGER PRIMARY KEY AUTOINCREMENT,
                        name    TEXT    NOT NULL,
                        contact TEXT    NOT NULL
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS receiver (
                        id      INTEGER PRIMARY KEY AUTOINCREMENT,
                        name    TEXT    NOT NULL,
                        contact TEXT    NOT NULL
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS raw_materials (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        name        TEXT    NOT NULL,
                        supplier_id INTEGER,
                        price       INTEGER NOT NULL DEFAULT 0,
                        quantity    INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS items (
                        itemId         INTEGER PRIMARY KEY AUTOINCREMENT,
                        name           TEXT    NOT NULL,
                        price_per_unit INTEGER NOT NULL DEFAULT 0,
                        createdAt      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS items_raw_materials (
                        itemId          INTEGER NOT NULL,
                        raw_material_id INTEGER NOT NULL,
                        PRIMARY KEY (itemId, raw_material_id),
                        FOREIGN KEY (itemId)          REFERENCES items(itemId),
                        FOREIGN KEY (raw_material_id) REFERENCES raw_materials(id)
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS orders (
                        orderId    INTEGER PRIMARY KEY AUTOINCREMENT,
                        name       TEXT      NOT NULL,
                        itemId     INTEGER   NOT NULL,
                        receiverId INTEGER   NOT NULL,
                        fulfilled  INTEGER   NOT NULL DEFAULT 0,
                        quantity   INTEGER   NOT NULL,
                        createdAt  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (itemId)     REFERENCES items(itemId),
                        FOREIGN KEY (receiverId) REFERENCES receiver(id)
                    )
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS finish_goods (
                        orderId    INTEGER PRIMARY KEY AUTOINCREMENT,
                        name       TEXT      NOT NULL,
                        itemId     INTEGER   NOT NULL,
                        receiverId INTEGER   NOT NULL,
                        fulfilled  INTEGER   NOT NULL DEFAULT 0,
                        quantity   INTEGER   NOT NULL,
                        createdAt  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

        } catch (SQLException e) {
            System.out.println("Error initializing tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void createIndexesIfNotExist(Connection connection) {
        String[] indexes = {
            "CREATE INDEX IF NOT EXISTS idx_raw_materials_supplier ON raw_materials(supplier_id)",
            "CREATE INDEX IF NOT EXISTS idx_items_raw_mat_item     ON items_raw_materials(itemId)",
            "CREATE INDEX IF NOT EXISTS idx_items_raw_mat_rm       ON items_raw_materials(raw_material_id)",
            "CREATE INDEX IF NOT EXISTS idx_orders_item            ON orders(itemId)",
            "CREATE INDEX IF NOT EXISTS idx_orders_receiver        ON orders(receiverId)",
            "CREATE INDEX IF NOT EXISTS idx_orders_fulfilled       ON orders(fulfilled)",
            "CREATE INDEX IF NOT EXISTS idx_finish_goods_item      ON finish_goods(itemId)",
            "CREATE INDEX IF NOT EXISTS idx_finish_goods_fulfilled ON finish_goods(fulfilled)"
        };

        try (Statement stmt = connection.createStatement()) {
            for (String sql : indexes) {
                stmt.executeUpdate(sql);
            }
        } catch (SQLException e) {
            System.out.println("Warning: Could not create indexes: " + e.getMessage());
        }
    }
}
