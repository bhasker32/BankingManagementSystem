package com.banking;

import com.banking.exception.BankingException;
import com.banking.service.BankService;
import com.banking.ui.BankingConsole;
import com.banking.ui.MainWindow;
import com.banking.ui.Theme;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class Main {
    private Main() { }
    public static void main(String[] args) {
        boolean console = args.length > 0 && args[0].equals("--console");
        if (console || (args.length > 0 && args[0].equals("--gui"))) { args = Arrays.copyOfRange(args, 1, args.length); }
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        if (args.length > 1) { out.println("Usage: java -jar smart-banking-management-system-1.0.0.jar [--gui|--console] [data-directory]"); System.exit(1); }
        if (!console) {
            if (GraphicsEnvironment.isHeadless()) { out.println("Desktop display unavailable. Run with --console for the console interface."); System.exit(1); }
            try {
                Path directory = Path.of(args.length == 0 ? "data" : args[0]);
                SwingUtilities.invokeLater(() -> { Theme.install(); new MainWindow(directory).setVisible(true); });
            } catch (InvalidPathException e) { out.println("Invalid data directory: " + e.getMessage()); System.exit(1); }
            return;
        }
        try (BankService bank = new BankService(Path.of(args.length == 0 ? "data" : args[0]), out::println)) {
            new BankingConsole(bank, new InputStreamReader(System.in, StandardCharsets.UTF_8), out).run();
        } catch (BankingException | IOException | InvalidPathException e) {
            out.println("Cannot continue: " + e.getMessage());
            System.exit(1);
        }
    }
}
