package com.banking.ui;

import java.awt.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Shared desktop design tokens and a small set of reusable components. */
public final class Theme {
    public static final Color NAVY = new Color(18, 32, 57), PRIMARY = new Color(39, 84, 197),
            BACKGROUND = new Color(244, 246, 250), SURFACE = Color.WHITE, BORDER = new Color(223, 228, 237),
            TEXT = new Color(26, 39, 61), MUTED = new Color(91, 106, 128), SUCCESS = new Color(24, 111, 83),
            ERROR = new Color(168, 43, 57), WARNING = new Color(132, 86, 18);
    public static final int GAP = 16, RADIUS = 16;
    private Theme() { }

    public static void install() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception e) { throw new IllegalStateException("Cannot initialize desktop appearance", e); }
        var font = new javax.swing.plaf.FontUIResource("Segoe UI", Font.PLAIN, 14);
        for (Object key : UIManager.getDefaults().keySet().toArray()) {
            if (UIManager.get(key) instanceof javax.swing.plaf.FontUIResource) { UIManager.put(key, font); }
        }
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TextField.background", SURFACE);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("ComboBox.background", SURFACE);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("Table.selectionBackground", new Color(227, 235, 252));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("ScrollBar.width", 12);
    }

    public static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setFont(new Font("Segoe UI", size >= 18 ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
    public static JPanel column() {
        JPanel p = new JPanel(); p.setOpaque(false); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); return p;
    }
    public static JPanel row(Component... components) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); p.setOpaque(false);
        for (Component c : components) { p.add(c); } return p;
    }
    public static JPanel card() {
        JPanel p = new JPanel(new BorderLayout(GAP, GAP)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D copy = (Graphics2D) g.create();
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                copy.setColor(SURFACE); copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
                copy.setColor(BORDER); copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS); copy.dispose();
            }
        };
        p.setOpaque(false); p.setBorder(new EmptyBorder(22, 22, 22, 22)); p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }
    public static JButton button(String text, String name, boolean primary, Runnable action) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D copy = (Graphics2D) g.create();
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean sidebar = Boolean.TRUE.equals(getClientProperty("sidebar"));
                Color base = sidebar ? (Boolean.TRUE.equals(getClientProperty("active")) ? new Color(43, 63, 98) : NAVY) : primary ? PRIMARY : SURFACE;
                copy.setColor(getModel().isRollover() ? (primary ? base.darker() : BACKGROUND) : base);
                if (sidebar && getModel().isRollover()) { copy.setColor(new Color(43, 63, 98)); }
                copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                copy.setColor(hasFocus() ? PRIMARY : sidebar ? base : (primary ? PRIMARY : BORDER));
                copy.setStroke(new BasicStroke(hasFocus() ? 3 : 1));
                copy.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 10, 10);
                copy.dispose(); super.paintComponent(g);
            }
        };
        b.setName(name); b.getAccessibleContext().setAccessibleName(text);
        b.setForeground(primary ? Color.WHITE : TEXT); b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setContentAreaFilled(false); b.setBorder(new EmptyBorder(11, 18, 11, 18)); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); b.addActionListener(e -> action.run()); return b;
    }
    public static JTextField input(String name, int columns) {
        JTextField field = new JTextField(columns); field.setName(name);
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), new EmptyBorder(10, 12, 10, 12)));
        return field;
    }
    public static JPanel field(String title, JComponent input, String hint) {
        JPanel p = column(); JLabel label = label(title, 14, TEXT); label.setLabelFor(input);
        input.getAccessibleContext().setAccessibleName(title);
        input.setAlignmentX(Component.LEFT_ALIGNMENT); input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        input.setMinimumSize(new Dimension(0, 44));
        p.add(label); p.add(Box.createVerticalStrut(7)); p.add(input);
        if (!hint.isEmpty()) { p.add(Box.createVerticalStrut(5)); p.add(label(hint, 12, MUTED)); }
        p.add(Box.createVerticalStrut(18)); return p;
    }
    public static void onChange(JTextField field, Runnable action) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { action.run(); }
            public void removeUpdate(DocumentEvent e) { action.run(); }
            public void changedUpdate(DocumentEvent e) { action.run(); }
        });
    }
    public static JTextArea message(String text) {
        JTextArea area = new JTextArea(text); area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true);
        area.setOpaque(false); area.setForeground(MUTED); area.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        area.setAlignmentX(Component.LEFT_ALIGNMENT); return area;
    }
    public static JScrollPane formBody(JComponent body) {
        class FormSurface extends JPanel implements Scrollable {
            FormSurface() { super(new BorderLayout()); setOpaque(false); add(body, BorderLayout.NORTH); }
            public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
            public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 20; }
            public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return Math.max(20, r.height - 20); }
            public boolean getScrollableTracksViewportWidth() { return true; }
            public boolean getScrollableTracksViewportHeight() { return false; }
        }
        JScrollPane scroll = new JScrollPane(new FormSurface()); scroll.setBorder(null); scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false); scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(20); return scroll;
    }
    public static void responsiveGuide(JPanel page, JComponent guide) {
        page.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                boolean show = page.getWidth() >= 900;
                if (guide.isVisible() != show) { guide.setVisible(show); page.revalidate(); }
            }
        });
    }
    public static String money(BigDecimal value) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.US); f.setMinimumFractionDigits(2); f.setMaximumFractionDigits(2);
        return "₹" + f.format(value);
    }
    public static String date(LocalDateTime time) { return time.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss", Locale.ENGLISH)); }
    public static String title(Enum<?> value) { String s = value.name().toLowerCase(Locale.ROOT); return Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}
