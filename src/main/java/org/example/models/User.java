package org.example.models;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

import static org.example.models.TablePrinter.*;

public abstract class User implements EntityHandler {
    protected int id;
    protected String name;
    protected String contactInfo;

    // Column config for Supplier / Receiver tables
    private static final String[] HEADERS = {"ID", "Name", "Contact"};
    private static final int[]    WIDTHS  = {5, 22, 24};
    private static final Align[]  ALIGNS  = {Align.RIGHT, Align.LEFT, Align.LEFT};

    public abstract String getTableName();
    public abstract void addUser(Connection connection, Scanner scanner) throws SQLException;
    public abstract void deleteUser(Connection connection, Scanner scanner) throws SQLException;
    public abstract void updateUser(Connection connection, Scanner scanner) throws SQLException;

    // ── Find All ─────────────────────────────────────────────────────────────────
    public void findAll(Connection connection) throws SQLException {
        if (connection == null) throw new SQLException("Connection cannot be null.");
        String tableName = getTableName();
        if (tableName == null || tableName.isBlank()) throw new SQLException("Invalid table name.");

        String query = "SELECT id, name, contact FROM " + tableName + " ORDER BY id";
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            printTopBorder(WIDTHS);
            printRow(HEADERS, WIDTHS, ALIGNS);
            printMidBorder(WIDTHS);

            boolean hasRows = false;
            while (rs.next()) {
                hasRows = true;
                printRow(new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("name"),
                        rs.getString("contact")
                }, WIDTHS, ALIGNS);
            }
            if (!hasRows) {
                printEmptyBox("  No records found.", totalInner(WIDTHS));
            }
            printBotBorder(WIDTHS);

        } catch (SQLException e) {
            System.out.println("Error retrieving records: " + e.getMessage());
            throw e;
        }
    }

    // ── Find By ID ────────────────────────────────────────────────────────────────
    public void findById(Connection connection, Scanner scanner) throws SQLException {
        if (connection == null) throw new SQLException("Connection cannot be null.");
        System.out.print("Enter " + getTableName() + " ID: ");
        int targetId = getValidId(scanner);

        String query = "SELECT id, name, contact FROM " + getTableName()
                + " WHERE id = ? LIMIT 1";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, targetId);
            try (ResultSet rs = stmt.executeQuery()) {
                printTopBorder(WIDTHS);
                printRow(HEADERS, WIDTHS, ALIGNS);
                printMidBorder(WIDTHS);
                if (rs.next()) {
                    printRow(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("name"),
                            rs.getString("contact")
                    }, WIDTHS, ALIGNS);
                } else {
                    printEmptyBox("  No record found with ID " + targetId, totalInner(WIDTHS));
                }
                printBotBorder(WIDTHS);
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving record: " + e.getMessage());
            throw e;
        }
    }

    // ── Input Helpers ─────────────────────────────────────────────────────────────
    public int getValidId(Scanner scanner) {
        while (true) {
            try {
                int val = Integer.parseInt(scanner.nextLine().trim());
                if (val <= 0) System.out.println("  Error: ID must be a positive integer.");
                else return val;
            } catch (NumberFormatException e) {
                System.out.println("  Error: Please enter a valid integer ID.");
            }
        }
    }

    public String getValidName(Scanner scanner) {
        while (true) {
            System.out.print("  Enter Name: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) System.out.println("  Error: Name cannot be empty.");
            else return input;
        }
    }

    public String getValidContactInfo(Scanner scanner) {
        while (true) {
            System.out.print("  Enter Contact Info (phone/email): ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) System.out.println("  Error: Contact info cannot be empty.");
            else return input;
        }
    }

    protected boolean existsById(Connection connection, int targetId) throws SQLException {
        String query = "SELECT EXISTS(SELECT 1 FROM " + getTableName() + " WHERE id = ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, targetId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    // Helper: total printable inner width across all columns
    private static int totalInner(int[] widths) {
        int total = 0;
        for (int w : widths) total += w + 3;
        return total - 3; // subtract last col's right padding+border
    }

    // Expose for subclasses
    protected static void printEmptyBox(String msg, int innerWidth) {
        TablePrinter.printEmptyBox(msg, innerWidth);
    }
}
