package org.example.models;

/**
 * Utility class for rendering formatted tables and menus in the console.
 * Uses Unicode box-drawing characters (supported by IntelliJ IDEA run console).
 *
 * Box chars:
 *  ╔ ═ ╦ ═ ╗   (top border)
 *  ║   ║   ║   (header row)
 *  ╠ ═ ╬ ═ ╣   (header separator)
 *  ║   ║   ║   (data rows)
 *  ╚ ═ ╩ ═ ╝   (bottom border)
 */
public class TablePrinter {

    public enum Align { LEFT, RIGHT }

    // ── Box characters ────────────────────────────────────────────────────────────
    private static final char TOP_L    = '╔', TOP_M    = '╦', TOP_R    = '╗';
    private static final char MID_L    = '╠', MID_M    = '╬', MID_R    = '╣';
    private static final char BOT_L    = '╚', BOT_M    = '╩', BOT_R    = '╝';
    private static final char H        = '═', V        = '║';
    private static final char THIN_L   = '╟', THIN_M   = '╫', THIN_R   = '╢';
    private static final char THIN_H   = '─';

    // ── Public API ────────────────────────────────────────────────────────────────

    /**
     * Print a full table: top border, header, separator, rows, bottom border.
     * @param headers  Column header labels
     * @param widths   Column inner widths (chars, not counting padding or borders)
     * @param aligns   Per-column alignment (defaults to LEFT if null)
     * @param rows     Data rows; each row must have headers.length entries
     * @param noDataMsg Message to show when rows is empty
     */
    public static void printTable(String[] headers, int[] widths,
                                  Align[] aligns, String[][] rows,
                                  String noDataMsg) {
        printTopBorder(widths);
        printRow(headers, widths, aligns);
        printMidBorder(widths);

        if (rows == null || rows.length == 0) {
            printEmptyRow(noDataMsg, totalInnerWidth(widths));
        } else {
            for (String[] row : rows) {
                printRow(row, widths, aligns);
            }
        }
        printBotBorder(widths);
    }

    /** Print only the top border (call before first row when streaming). */
    public static void printTopBorder(int[] widths) {
        System.out.println(buildSeparator(widths, TOP_L, H, TOP_M, TOP_R));
    }

    /** Print the thick separator (used after header row). */
    public static void printMidBorder(int[] widths) {
        System.out.println(buildSeparator(widths, MID_L, H, MID_M, MID_R));
    }

    /** Print a thin separator (used between data rows or sections). */
    public static void printThinBorder(int[] widths) {
        System.out.println(buildSeparator(widths, THIN_L, THIN_H, THIN_M, THIN_R));
    }

    /** Print the bottom border (call after last row). */
    public static void printBotBorder(int[] widths) {
        System.out.println(buildSeparator(widths, BOT_L, H, BOT_M, BOT_R));
    }

    /** Print a single data row. */
    public static void printRow(String[] values, int[] widths, Align[] aligns) {
        StringBuilder sb = new StringBuilder();
        sb.append(V);
        for (int i = 0; i < widths.length; i++) {
            String val = (values != null && i < values.length && values[i] != null)
                    ? values[i] : "";
            Align align = (aligns != null && i < aligns.length && aligns[i] != null)
                    ? aligns[i] : Align.LEFT;
            sb.append(' ').append(cell(val, widths[i], align)).append(' ').append(V);
        }
        System.out.println(sb);
    }

    /**
     * Print a banner title block.
     * ╔══════════════════════╗
     * ║   TITLE              ║
     * ╚══════════════════════╝
     */
    public static void printTitle(String title) {
        int inner = title.length() + 4;
        String top = TOP_L + repeat(H, inner) + TOP_R;
        String mid = V + "  " + title + "  " + V;
        String bot = BOT_L + repeat(H, inner) + BOT_R;
        System.out.println("\n" + top);
        System.out.println(mid);
        System.out.println(bot);
    }

    /**
     * Print a section header (lighter style).
     *  ─── Section ───────
     */
    public static void printSection(String label, int totalWidth) {
        String dashes = repeat('─', totalWidth - label.length() - 5);
        System.out.println("  ─── " + label + " " + dashes);
    }

    /**
     * Print a key-value dashboard row inside a box.
     *  ║  Label                  value  ║
     */
    public static void printDashRow(String label, String value, int innerWidth) {
        String line = "  " + padRight(label, innerWidth - value.length() - 2) + value;
        System.out.println(V + " " + padRight(line, innerWidth) + " " + V);
    }

    public static void printDivider(int innerWidth) {
        System.out.println(THIN_L + repeat(THIN_H, innerWidth + 2) + THIN_R);
    }

    public static void printEmptyBox(String msg, int innerWidth) {
        System.out.println(V + "  " + padRight(msg, innerWidth) + "  " + V);
    }

    // ── Formatting helpers ────────────────────────────────────────────────────────

    /** Format a value into a cell of given width with given alignment. */
    public static String cell(String s, int width, Align align) {
        if (s == null) s = "";
        if (s.length() > width) s = s.substring(0, width - 1) + "…";
        if (align == Align.RIGHT) return padLeft(s, width);
        return padRight(s, width);
    }

    public static String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width);
        return s + repeat(' ', width - s.length());
    }

    public static String padLeft(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width);
        return repeat(' ', width - s.length()) + s;
    }

    // ── Private builders ──────────────────────────────────────────────────────────

    private static String buildSeparator(int[] widths, char left, char fill,
                                         char mid, char right) {
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int i = 0; i < widths.length; i++) {
            sb.append(repeat(fill, widths[i] + 2));
            sb.append(i < widths.length - 1 ? mid : right);
        }
        return sb.toString();
    }

    private static void printEmptyRow(String msg, int innerWidth) {
        // span all columns
        System.out.println(V + "  " + padRight(msg, innerWidth - 2) + V);
    }

    private static int totalInnerWidth(int[] widths) {
        int total = 0;
        for (int w : widths) total += w + 3; // +2 padding +1 separator
        return total - 1; // last separator is right border
    }

    private static String repeat(char c, int count) {
        if (count <= 0) return "";
        return String.valueOf(c).repeat(count);
    }
}
