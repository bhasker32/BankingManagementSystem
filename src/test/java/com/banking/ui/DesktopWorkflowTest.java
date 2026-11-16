package com.banking.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real desktop tests: drives actual Swing controls and modal confirmations. */
@EnabledIfSystemProperty(named = "banking.guiTests", matches = "true")
class DesktopWorkflowTest {
    @TempDir Path data;
    MainWindow window;

    @BeforeEach void launch() throws Exception {
        assertFalse(GraphicsEnvironment.isHeadless(), "GUI verification requires an interactive desktop.");
        edt(() -> { Theme.install(); window = new MainWindow(data); window.setVisible(true); return null; });
        await(() -> window.snapshot() != null);
    }
    @AfterEach void cleanup() throws Exception {
        edt(() -> { for (Window w : Window.getWindows()) { if (w instanceof JDialog) { w.dispose(); } } if (window != null) { window.requestClose(); } return null; });
        if (window != null) { await(() -> !window.isDisplayable()); }
    }

    @Test void completeDesktopWorkflowAndRestart() throws Exception {
        capture("empty-dashboard");
        create("Alice Example", "alice@example.com", 0);
        create("Bob Example", "bob@example.com", 1);
        money("Deposit", 0, "10000", true);
        money("Deposit", 1, "5000", true);
        money("Deposit", 0, "2000", true);
        money("Withdraw", 0, "2000", false); // cancel confirmation: no write
        assertEquals(new BigDecimal("12000.00"), edt(() -> window.snapshot().accounts().get(0).getBalance()));
        money("Withdraw", 0, "2000", true);
        click("nav-Transfer"); awaitComponent("amount"); fill("amount", "3000"); fill("transfer-note", "Monthly allocation");
        click("review-money"); awaitComponent("confirm-money"); capture("transfer-review");
        assertEquals(4, edt(() -> window.snapshot().transactionCount()));
        click("confirm-money"); awaitComponent("close-receipt"); click("close-receipt"); awaitNoDialog();
        assertBalances();
        click("nav-Dashboard"); awaitComponent("recent-transactions"); capture("dashboard");
        edt(() -> { window.setSize(980, 700); return null; }); Thread.sleep(250);
        await(() -> find("quick-Transactions").getY() > find("quick-Create Account").getY()); capture("dashboard-compact");
        edt(() -> { window.setSize(1380, 920); return null; });
        click("nav-Accounts"); awaitComponent("account-search"); fill("account-search", "alice");
        assertEquals(1, edt(() -> ((JTable) find("accounts-table")).getRowCount()));
        fill("account-search", "CUST100002"); assertEquals(1, edt(() -> ((JTable) find("accounts-table")).getRowCount()));
        fill("account-search", ""); select("account-filter", 1); assertEquals(1, edt(() -> ((JTable) find("accounts-table")).getRowCount()));
        select("account-filter", 0); capture("accounts");
        edt(() -> { ((JTable) find("accounts-table")).setRowSelectionInterval(0, 0); return null; });
        click("view-account"); awaitComponent("account-number"); capture("account-details");
        click("nav-Transactions"); awaitComponent("transaction-search"); fill("transaction-search", "Monthly allocation");
        assertEquals(2, edt(() -> ((JTable) find("transactions-table")).getRowCount()));
        select("transaction-filter", 1); await(() -> find("transactions-table") == null);
        select("transaction-filter", 3); awaitComponent("transactions-table"); capture("transactions");
        click("refresh"); await(() -> !((JTextArea) find("notice")).getText().isBlank() && ((JTextArea) find("notice")).getText().contains("refreshed"));
        edt(() -> { window.requestClose(); return null; }); await(() -> !window.isDisplayable());
        edt(() -> { window = new MainWindow(data); window.setVisible(true); return null; }); await(() -> window.snapshot() != null); assertBalances();
        assertEquals(6, edt(() -> window.snapshot().transactions().size()));
        click("nav-Transfer"); awaitComponent("amount"); fill("amount", "20000"); click("review-money");
        await(() -> ((JTextArea) find("money-errors")).getText().contains("Insufficient")); assertBalances(); capture("transfer-error");
        select("destination-account", 0); fill("amount", "1"); click("review-money");
        await(() -> ((JTextArea) find("money-errors")).getText().contains("different")); assertBalances();
        click("nav-Withdraw"); awaitComponent("amount"); fill("amount", "99999"); click("review-money");
        await(() -> ((JTextArea) find("money-errors")).getText().contains("Insufficient")); assertBalances();
        fill("amount", "abc"); click("review-money"); assertTrue(edt(() -> ((JTextArea) find("money-errors")).getText().contains("numeric")));
        edt(() -> { window.setSize(980, 700); return null; }); Thread.sleep(250); capture("withdraw-compact");
        assertTrue(edt(() -> { JComponent button = (JComponent) find("review-money"); return button.getVisibleRect().width == button.getWidth(); }));
        assertTrue(edt(() -> { JComponent combo = (JComponent) find("source-account"); return combo.getVisibleRect().width == combo.getWidth(); }));
    }

    @Test void refreshFailureRemainsUsableAndCanRecover() throws Exception {
        create("Alice Example", "alice@example.com", 0);
        String original = Files.readString(data.resolve("accounts.txt")); Files.writeString(data.resolve("accounts.txt"), "broken");
        click("refresh"); await(() -> ((JTextArea) find("notice")).getText().contains("Refresh failed"));
        assertEquals(1, edt(() -> window.snapshot().accounts().size()));
        Files.writeString(data.resolve("accounts.txt"), original); click("refresh"); await(() -> ((JTextArea) find("notice")).getText().contains("refreshed"));
        click("nav-Settings / About"); awaitComponent("data-path"); assertEquals(data.toAbsolutePath().toString(), edt(() -> ((JTextField) find("data-path")).getText()));
    }

    @Test void startupErrorCanRetryAndUnexpectedFailureDoesNotCloseWindow() throws Exception {
        edt(() -> { window.requestClose(); return null; }); await(() -> !window.isDisplayable());
        Files.writeString(data.resolve("accounts.txt"), "malformed");
        edt(() -> { window = new MainWindow(data); window.setVisible(true); return null; }); awaitComponent("retry");
        assertTrue(edt(() -> ((JTextArea) find("notice")).getText().contains("Malformed")));
        Files.writeString(data.resolve("accounts.txt"), ""); click("retry"); await(() -> window.snapshot() != null);
        edt(() -> { window.task("Checking error state…", () -> { throw new IllegalStateException("internal details"); }, ignored -> fail()); return null; });
        await(() -> ((JTextArea) find("notice")).getText().contains("Unable to complete"));
        assertFalse(edt(() -> ((JTextArea) find("notice")).getText().contains("internal details")));
        click("nav-Accounts"); awaitComponent("create-account");
    }

    @Test void closingWaitsForAnInFlightWorkerAndReleasesTheStore() throws Exception {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        edt(() -> { window.task("Finishing work…", () -> { release.await(); return null; }, ignored -> { }); window.requestClose(); return null; });
        assertTrue(edt(() -> window.isDisplayable())); release.countDown(); await(() -> !window.isDisplayable());
        try (var bank = new com.banking.service.BankService(data, message -> fail(message))) { assertEquals(0, bank.snapshot().accounts().size()); }
    }

    private void create(String name, String email, int type) throws Exception {
        click("nav-Accounts"); awaitComponent("create-account"); click("create-account"); awaitComponent("customer-name");
        fill("customer-name", name); fill("customer-phone", "9876543210"); fill("customer-email", email); select("customer-type", type);
        click("submit-create"); awaitComponent("account-number");
    }
    private void money(String page, int account, String amount, boolean confirm) throws Exception {
        click("nav-" + page); awaitComponent("amount"); select("source-account", account); fill("amount", amount); click("review-money"); awaitComponent("confirm-money");
        click(confirm ? "confirm-money" : "cancel-confirm");
        if (confirm) { awaitComponent("close-receipt"); click("close-receipt"); } awaitNoDialog();
    }
    private void assertBalances() throws Exception {
        assertEquals(new BigDecimal("7000.00"), edt(() -> window.snapshot().accounts().get(0).getBalance()));
        assertEquals(new BigDecimal("8000.00"), edt(() -> window.snapshot().accounts().get(1).getBalance()));
        assertEquals(5, edt(() -> window.snapshot().transactionCount()));
    }
    private void capture(String name) throws Exception {
        Path dir = Path.of(System.getProperty("banking.buildDirectory", "target"), "gui-evidence"); Files.createDirectories(dir);
        edt(() -> {
            Window target = window; for (Window w : window.getOwnedWindows()) { if (w.isVisible()) { target = w; } }
            BufferedImage image = new BufferedImage(target.getWidth(), target.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics(); target.paint(graphics); graphics.dispose(); ImageIO.write(image, "png", dir.resolve(name + ".png").toFile()); return null;
        });
    }
    private void click(String name) throws Exception {
        awaitComponent(name); SwingUtilities.invokeLater(() -> ((AbstractButton) find(name)).doClick());
    }
    private void fill(String name, String text) throws Exception { edt(() -> { ((JTextField) find(name)).setText(text); return null; }); }
    private void select(String name, int index) throws Exception { edt(() -> { ((JComboBox<?>) find(name)).setSelectedIndex(index); return null; }); }
    private void awaitComponent(String name) throws Exception { await(() -> { Component c = find(name); return c != null && c.isShowing() && c.isEnabled(); }); }
    private void awaitNoDialog() throws Exception { await(() -> { for (Window w : window.getOwnedWindows()) { if (w.isVisible()) { return false; } } return true; }); }
    private Component find(String name) {
        for (Window w : window.getOwnedWindows()) { if (w.isVisible()) { Component found = findIn(w, name); if (found != null) { return found; } } }
        return findIn(window, name);
    }
    private Component findIn(Component c, String name) {
        if (name.equals(c.getName())) { return c; }
        if (c instanceof Container parent) { for (Component child : parent.getComponents()) { Component found = findIn(child, name); if (found != null) { return found; } } }
        return null;
    }
    private void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < deadline) { if (edt(condition)) { return; } Thread.sleep(40); }
        fail("Timed out waiting for desktop state");
    }
    private <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> result = new FutureTask<>(action); SwingUtilities.invokeAndWait(result); return result.get();
    }
}
