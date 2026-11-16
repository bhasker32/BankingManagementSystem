package com.banking.ui;

import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.table.*;

/** Read-only, sortable table with an explicit empty state and keyboard activation. */
final class DataTable extends JPanel {
    private final DefaultTableModel model;
    private final JTable table;
    private final JLabel empty = Theme.label("No results found.", 14, Theme.MUTED);
    private final JScrollPane scroll;

    DataTable(String name, String[] columns, Class<?>[] types, int height) {
        super(new BorderLayout()); setOpaque(false);
        model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int column) { return types[column]; }
        };
        table = new JTable(model); table.setName(name); table.setRowHeight(46); table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0)); table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setAutoCreateRowSorter(true);
        table.setBackground(Theme.SURFACE); table.setForeground(Theme.TEXT);
        table.getTableHeader().setPreferredSize(new Dimension(1, 38));
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setDefaultRenderer((t, value, selected, focus, row, col) -> {
            JLabel label = Theme.label(String.valueOf(value), 12, Theme.MUTED);
            label.setOpaque(true); label.setBackground(Theme.BACKGROUND); label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10)); return label;
        });
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                String text = value instanceof BigDecimal amount ? Theme.money(amount) : value instanceof LocalDateTime time ? Theme.date(time) : String.valueOf(value);
                super.getTableCellRendererComponent(t, text, selected, focus, row, column);
                putClientProperty("html.disable", Boolean.TRUE);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                setHorizontalAlignment(value instanceof BigDecimal ? RIGHT : LEFT);
                if (!selected) { setBackground(row % 2 == 0 ? Theme.SURFACE : new Color(249, 250, 253)); setForeground(Theme.TEXT); }
                if (text.equals("Success") || text.equals("Active")) { setForeground(Theme.SUCCESS); }
                if (text.equals("Failed")) { setForeground(Theme.ERROR); }
                setToolTipText(text); return this;
            }
        };
        for (Class<?> type : types) { table.setDefaultRenderer(type, renderer); }
        scroll = new JScrollPane(table); scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.setPreferredSize(new Dimension(600, height)); scroll.getViewport().setBackground(Theme.SURFACE);
        empty.setBorder(BorderFactory.createEmptyBorder(28, 12, 28, 12)); add(scroll);
    }
    void rows(List<Object[]> rows, String emptyText) {
        model.setRowCount(0); rows.forEach(model::addRow); empty.setText(emptyText);
        removeAll(); add(rows.isEmpty() ? empty : scroll); revalidate(); repaint();
    }
    void activate(Consumer<Integer> action) {
        table.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) { selected(action); } } });
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openDetails");
        table.getActionMap().put("openDetails", new AbstractAction() { public void actionPerformed(ActionEvent e) { selected(action); } });
    }
    void selected(Consumer<Integer> action) {
        int row = table.getSelectedRow(); if (row >= 0) { action.accept(table.convertRowIndexToModel(row)); }
    }
    JTable table() { return table; }
}
