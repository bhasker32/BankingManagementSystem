package com.banking.ui;

import com.banking.exception.BankingException;
import com.banking.model.*;
import com.banking.service.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Single desktop window. All service I/O runs on SwingWorker, all widgets on the EDT. */
public final class MainWindow extends JFrame {
    private final Path dataDirectory;
    private BankService bank;
    private BankSnapshot snapshot;
    private final JPanel content = new JPanel(new BorderLayout());
    private final JLabel heading = Theme.label("Overview", 28, Theme.TEXT);
    private final JLabel subtitle = Theme.label("Your banking workspace, at a glance.", 14, Theme.MUTED);
    private final JTextArea notice = Theme.message("Loading banking data…");
    private final JLabel state = Theme.label("Loading…", 12, Theme.MUTED);
    private final LinkedHashMap<String, JButton> navigation = new LinkedHashMap<>();
    private final JPanel busyGlass = new JPanel(new GridBagLayout());
    private final JLabel busyLabel = Theme.label("Loading banking data…", 18, Theme.TEXT);
    private boolean busy;
    private boolean closeRequested;
    private String page = "Dashboard";
    private String context;
    private String pendingWarning;

    public MainWindow(Path dataDirectory) {
        super("SmartBank · Banking workspace"); this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        setName("smartbank-window"); setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(980, 700)); setSize(1380, 920); setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout()); root.setBackground(Theme.BACKGROUND); setContentPane(root);
        root.add(sidebar(), BorderLayout.WEST);
        JPanel workspace = new JPanel(new BorderLayout()); workspace.setOpaque(false); root.add(workspace);
        JPanel top = new JPanel(new BorderLayout()); top.setBackground(Theme.SURFACE); top.setBorder(new EmptyBorder(18, 28, 18, 28));
        top.add(Theme.label("WORKSPACE   /   BANKING OPERATIONS", 12, Theme.MUTED), BorderLayout.WEST);
        top.add(Theme.label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", Locale.ENGLISH)) + "    •    Local session", 13, Theme.MUTED), BorderLayout.EAST);
        workspace.add(top, BorderLayout.NORTH);
        JPanel main = new JPanel(new BorderLayout(0, 20)); main.setOpaque(false); main.setBorder(new EmptyBorder(28, 28, 16, 28));
        workspace.add(main);
        JPanel title = new JPanel(new BorderLayout()); title.setOpaque(false);
        JPanel titles = Theme.column(); titles.add(heading); titles.add(Box.createVerticalStrut(6)); titles.add(subtitle); title.add(titles);
        title.add(Theme.button("Refresh data", "refresh", false, this::refresh), BorderLayout.EAST);
        main.add(title, BorderLayout.NORTH); content.setOpaque(false); main.add(content);
        JPanel footer = Theme.column(); notice.setName("notice"); notice.setRows(2); footer.add(notice); footer.add(state); main.add(footer, BorderLayout.SOUTH);
        busyGlass.setOpaque(false); busyGlass.add(busyLabel); busyGlass.setFocusTraversalKeysEnabled(false);
        busyGlass.addMouseListener(new MouseAdapter() { }); busyGlass.addKeyListener(new KeyAdapter() { });
        setGlassPane(busyGlass);
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { requestClose(); } });
        load();
    }

    private JPanel sidebar() {
        JPanel side = new JPanel(new BorderLayout()); side.setBackground(Theme.NAVY); side.setPreferredSize(new Dimension(212, 1));
        side.setBorder(new EmptyBorder(30, 18, 24, 18));
        JPanel brand = Theme.column(); brand.add(Theme.label("S /  SmartBank", 24, Color.WHITE)); brand.add(Box.createVerticalStrut(8));
        brand.add(Theme.label("BANKING MANAGEMENT", 10, new Color(158, 177, 207))); brand.add(Box.createVerticalStrut(38)); side.add(brand, BorderLayout.NORTH);
        JPanel links = Theme.column(); links.add(Theme.label("WORKSPACE", 10, new Color(158, 177, 207))); links.add(Box.createVerticalStrut(14));
        for (String name : new String[]{"Dashboard", "Accounts", "Deposit", "Withdraw", "Transfer", "Transactions", "Settings / About"}) {
            JButton b = Theme.button(name, "nav-" + name, false, () -> navigate(name, null));
            b.putClientProperty("sidebar", Boolean.TRUE); b.setForeground(new Color(205, 217, 237));
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46)); b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setHorizontalAlignment(SwingConstants.LEFT); navigation.put(name, b); links.add(b); links.add(Box.createVerticalStrut(10));
        }
        side.add(links);
        JPanel bottom = Theme.column(); bottom.add(Theme.label("EDUCATIONAL EDITION", 10, new Color(158, 177, 207)));
        bottom.add(Box.createVerticalStrut(12)); bottom.add(Theme.button("Exit application", "exit", false, this::requestClose)); side.add(bottom, BorderLayout.SOUTH); return side;
    }

    private void load() {
        task("Loading banking data…", () -> new BankService(dataDirectory, warning -> pendingWarning = warning), loaded -> {
            bank = loaded; snapshot = bank.snapshot(); navigate("Dashboard", null);
            success(snapshot.accounts().isEmpty() ? "No existing banking data found. Create your first account to get started."
                    : "Data loaded successfully. " + snapshot.accounts().size() + " accounts · " + snapshot.transactionCount() + " transactions.");
        }, error -> {
            error(error); JPanel panel = Theme.card(); JPanel copy = Theme.column();
            copy.add(Theme.label("Unable to open banking data", 22, Theme.TEXT)); copy.add(Box.createVerticalStrut(14));
            copy.add(Theme.message("Check the data directory and close any other SmartBank session using it. You can retry after resolving the issue."));
            panel.add(copy); panel.add(Theme.button("Retry loading", "retry", true, this::load), BorderLayout.SOUTH); setPage(panel);
        });
    }
    BankService bank() { return bank; }
    BankSnapshot snapshot() { return snapshot; }
    void navigate(String name, String selected) {
        if (busy || bank == null) { return; } page = name; context = selected;
        heading.setText(name.equals("Dashboard") ? "Overview" : name);
        subtitle.setText(switch (name) {
            case "Dashboard" -> "A clear view of your accounts and latest activity.";
            case "Accounts" -> "Find an account, view customer details, and manage balances.";
            case "Transactions" -> "Every completed operation, with a complete account trail.";
            case "Create Account" -> "Start a new customer relationship.";
            case "Account Details" -> "Customer information and account activity.";
            case "Settings / About" -> "Application information and workspace preferences.";
            default -> "Choose an account and review the details before completing your transaction.";
        });
        navigation.forEach((key, button) -> { button.putClientProperty("active", key.equals(name)); button.setForeground(key.equals(name) ? Color.WHITE : new Color(205, 217, 237)); button.setFont(button.getFont().deriveFont(key.equals(name) ? Font.BOLD : Font.PLAIN)); button.repaint(); });
        JComponent view = switch (name) {
            case "Dashboard" -> new DashboardView(this);
            case "Accounts" -> new AccountsView(this);
            case "Create Account" -> new CreateAccountView(this);
            case "Account Details" -> AccountsView.details(this, selected);
            case "Deposit" -> new MoneyView(this, TransactionType.DEPOSIT, selected);
            case "Withdraw" -> new MoneyView(this, TransactionType.WITHDRAWAL, selected);
            case "Transfer" -> new MoneyView(this, TransactionType.TRANSFER, selected);
            case "Transactions" -> new TransactionsView(this, selected);
            default -> about();
        };
        setPage(view);
    }
    private void setPage(JComponent view) {
        content.removeAll();
        if (view instanceof MoneyView || view instanceof CreateAccountView) { content.add(view); }
        else {
            JScrollPane scroll = new JScrollPane(new PageSurface(view)); scroll.setBorder(null); scroll.getViewport().setBackground(Theme.BACKGROUND);
            scroll.getVerticalScrollBar().setUnitIncrement(24); content.add(scroll);
        }
        content.revalidate(); content.repaint();
    }
    private static final class PageSurface extends JPanel implements Scrollable {
        PageSurface(JComponent view) { super(new BorderLayout()); setOpaque(false); add(view, BorderLayout.NORTH); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 24; }
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return Math.max(24, r.height - 24); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return getParent() != null && getPreferredSize().height < getParent().getHeight(); }
    }
    void refresh() {
        if (bank == null) { if (!busy) { load(); } return; }
        task("Refreshing saved data…", bank::refresh, fresh -> { snapshot = fresh; navigate(page, context); success("Data refreshed successfully."); });
    }
    <T> void mutate(String activity, Callable<T> operation, Consumer<T> completed) {
        task(activity, () -> { T result = operation.call(); return new Change<>(result, bank.snapshot()); }, change -> {
            snapshot = change.snapshot(); completed.accept(change.result());
        });
    }
    private record Change<T>(T result, BankSnapshot snapshot) { }
    <T> void task(String activity, Callable<T> operation, Consumer<T> completed) { task(activity, operation, completed, this::error); }
    <T> void task(String activity, Callable<T> operation, Consumer<T> completed, Consumer<Throwable> failed) {
        if (busy) { return; } busy = true; state.setText(activity); busyLabel.setText(activity);
        setEnabledTree(getContentPane(), false); busyGlass.setVisible(true); busyGlass.requestFocusInWindow();
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return operation.call(); }
            @Override protected void done() {
                busy = false; busyGlass.setVisible(false); setEnabledTree(getContentPane(), true);
                state.setText("Local workspace  •  Every completed operation is saved automatically");
                try { T result = get(); if (!closeRequested || bank == null) { completed.accept(result); } }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); failed.accept(e); }
                catch (ExecutionException e) { failed.accept(e.getCause()); }
                catch (RuntimeException e) { failed.accept(e); }
                if (pendingWarning != null) { notice.setText(pendingWarning); notice.setForeground(Theme.WARNING); pendingWarning = null; }
                if (closeRequested) { requestClose(); }
            }
        }.execute();
    }
    private void setEnabledTree(Component c, boolean enabled) {
        c.setEnabled(enabled); if (c instanceof Container container) { for (Component child : container.getComponents()) { setEnabledTree(child, enabled); } }
    }
    void success(String message) { notice.setText("Success · " + message); notice.setForeground(Theme.SUCCESS); }
    void error(Throwable error) {
        notice.setText(error instanceof BankingException ? error.getMessage() : "Unable to complete this action. Refresh the data and try again."); notice.setForeground(Theme.ERROR);
    }
    void requestClose() {
        if (busy) { closeRequested = true; state.setText("Finishing the current operation before closing…"); return; }
        closeRequested = false;
        if (bank == null) { dispose(); return; }
        task("Closing workspace…", () -> { bank.close(); return null; }, ignored -> dispose());
    }
    private JComponent about() {
        JPanel card = Theme.card(); JPanel body = Theme.column(); body.add(Theme.label("Smart Banking Management System", 22, Theme.TEXT));
        body.add(Box.createVerticalStrut(12)); body.add(Theme.label("Version 1.0.0  /  Java 17 + Swing", 14, Theme.MUTED)); body.add(Box.createVerticalStrut(24));
        body.add(Theme.message("Account management, deposits, withdrawals, transfers and transaction history.\n\nThis is an educational banking simulation. This local workspace has no authentication, encryption or real payment integration.\n\nSavings interest is an annual estimate of 4%; it is not automatically credited. Current accounts have a zero overdraft limit.\n\nData location"));
        JTextField path = Theme.input("data-path", 30); path.setText(dataDirectory.toString()); path.setEditable(false); body.add(Box.createVerticalStrut(14)); body.add(path);
        body.add(Box.createVerticalStrut(20)); body.add(Theme.message("Use Refresh data to reload saved records. Only one application can open this data directory at a time.\n\nKeyboard: Tab moves between controls; Enter opens a selected table row. Table headers sort the displayed results."));
        card.add(body, BorderLayout.NORTH); return card;
    }
}
