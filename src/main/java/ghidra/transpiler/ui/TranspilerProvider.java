package ghidra.transpiler.ui;

import docking.ComponentProvider;
import docking.WindowPosition;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileOptions;
import ghidra.framework.plugintool.Plugin;
import ghidra.util.Msg;
import resources.ResourceManager;
import javax.swing.ImageIcon;
import generic.theme.GColor;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;
import ghidra.util.task.ConsoleTaskMonitor;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TranspilerProvider extends ComponentProvider {

    private JTextPane outputArea;
    private JLabel statusLabel;
    private JPanel mainPanel;

    private Program currentProgram;
    private Function currentFunction;

    public TranspilerProvider(Plugin plugin) {
        super(plugin.getTool(), "Transpiler", plugin.getName());
        buildUI();
        setTitle("GhidraTranspiler");
        setDefaultWindowPosition(WindowPosition.RIGHT);
        try {
            ImageIcon icon = ResourceManager.loadImage("images/cpp_icon.png");
            if (icon != null) {
                setIcon(icon);
            }
        } catch (Exception e) {
            Msg.debug(this, "GhidraTranspiler: Could not load icon: " + e.getMessage());
        }
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    private void buildUI() {
        mainPanel = new JPanel(new BorderLayout(6, 6));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

        JButton copyBtn = new JButton("Copy");
        copyBtn.addActionListener(e -> copyToClipboard());
        topBar.add(copyBtn);

        mainPanel.add(topBar, BorderLayout.NORTH);

        outputArea = new JTextPane() {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return getUI().getPreferredSize(this).width <= getParent().getSize().width;
            }
        };
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        outputArea.setEditable(false);
        outputArea.setBackground(new Color(28, 28, 28));
        outputArea.setForeground(new Color(212, 212, 212));
        outputArea.setCaretColor(Color.WHITE);
        JScrollPane scroll = new JScrollPane(outputArea);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
        mainPanel.add(scroll, BorderLayout.CENTER);

        statusLabel = new JLabel("Ready.");
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);
    }

    public void transpile(Program program, Function function) {
        this.currentProgram = program;
        this.currentFunction = function;
        setSubTitle("'" + function.getPrototypeString(false, false) + "'");
        runTranspile();
    }

    private void runTranspile() {
        if (currentProgram == null || currentFunction == null) {
            outputArea.setText("Place cursor inside a function to auto-transpile.\nOr use Tools > GhidraTranspiler menu.");
            return;
        }

        statusLabel.setText("Transpiling " + currentFunction.getName() + " \u2192 C++...");
        outputArea.setText("Working...");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                DecompInterface decomp = new DecompInterface();
                DecompileOptions opts = new DecompileOptions();
                decomp.setOptions(opts);
                decomp.setSimplificationStyle("decompile");
                decomp.openProgram(currentProgram);

                ghidra.app.decompiler.DecompileResults res = decomp.decompileFunction(currentFunction, 60, new ConsoleTaskMonitor());
                if (res != null && res.decompileCompleted()) {
                    ghidra.app.decompiler.ClangTokenGroup markup = res.getCCodeMarkup();
                    return convertToCpp(markup, currentFunction);
                }

                return "Error: Decompiler failed to produce output.";
            }

            private String convertToCpp(ghidra.app.decompiler.ClangTokenGroup group, Function function) {
                ghidra.app.decompiler.PrettyPrinter printer = new ghidra.app.decompiler.PrettyPrinter(function, group, null);
                java.util.List<ghidra.app.decompiler.ClangLine> lines = printer.getLines();

                StringBuilder sb = new StringBuilder();
                sb.append("#include <cstdint>\n\n");

                for (ghidra.app.decompiler.ClangLine line : lines) {
                    sb.append(line.getIndentString());
                    java.util.List<ghidra.app.decompiler.ClangToken> tokens = line.getAllTokens();
                    for (ghidra.app.decompiler.ClangToken t : tokens) {
                        String text = t.getText();
                        switch (text) {
                            case "longlong":   text = "int64_t"; break;
                            case "ulonglong":  text = "uint64_t"; break;
                            case "int":        text = "int32_t"; break;
                            case "uint":       text = "uint32_t"; break;
                            case "short":      text = "int16_t"; break;
                            case "ushort":     text = "uint16_t"; break;
                            case "char":       text = "int8_t"; break;
                            case "byte":       text = "uint8_t"; break;
                            case "undefined8": text = "uint64_t"; break;
                            case "undefined4": text = "uint32_t"; break;
                            case "undefined2": text = "uint16_t"; break;
                            case "undefined1": text = "uint8_t"; break;
                            case "undefined":  text = "uint8_t"; break;
                            case "NULL":       text = "nullptr"; break;
                        }
                        sb.append(text);
                    }
                    sb.append("\n");
                }

                String cpp = sb.toString();

                Pattern castPtrPattern = Pattern.compile(
                    "\\(\\s*([\\w]++(?:\\s++[\\w]++)*+)\\s*\\*\\s*\\)\\s*" +
                    "([a-zA-Z_][a-zA-Z0-9_\\.\\[\\]]*+|-?0x[0-9a-fA-F]++|-?\\d++)(?![a-zA-Z0-9_(])"
                );
                cpp = castPtrPattern.matcher(cpp).replaceAll("reinterpret_cast<$1*>($2)");

                Pattern castPattern = Pattern.compile(
                    "\\(\\s*(u?int(?:8|16|32|64)_t|bool|float|double)\\s*\\)\\s*" +
                    "([a-zA-Z_][a-zA-Z0-9_\\.\\[\\]]*+|-?0x[0-9a-fA-F]++|-?\\d++)(?![a-zA-Z0-9_(])"
                );
                cpp = castPattern.matcher(cpp).replaceAll("static_cast<$1>($2)");

                return cpp;
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    highlightSyntax(result);
                    outputArea.setCaretPosition(0);
                    String sig = currentFunction.getPrototypeString(false, false);
                    setSubTitle("'" + sig + "'");
                    statusLabel.setText("Done \u2014 " + currentFunction.getName() + " \u2192 C++");
                } catch (Exception ex) {
                    outputArea.setText("ERROR: " + ex.getMessage() + "\n\n" +
                                       "Stack trace:\n" +
                                       java.util.Arrays.stream(ex.getStackTrace())
                                           .map(StackTraceElement::toString)
                                           .reduce("", (a, b) -> a + "\n" + b));
                    statusLabel.setText("Error: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void highlightSyntax(String text) {
        outputArea.setText(text);
        StyledDocument doc = outputArea.getStyledDocument();

        StyleContext ctx = StyleContext.getDefaultStyleContext();
        Color bgColor     = new GColor("color.bg.decompiler");
        Color normalColor = new GColor("color.fg.decompiler.variable");
        Color kwdColor    = new GColor("color.fg.decompiler.keyword");
        Color typColor    = new GColor("color.fg.decompiler.type");
        Color castColor   = new GColor("color.fg.decompiler.function");
        Color numColor    = new GColor("color.fg.decompiler.constant");
        Color cmtColor    = new GColor("color.fg.decompiler.comment");
        Color prepColor   = new GColor("color.fg.decompiler.global");

        outputArea.setBackground(bgColor);

        AttributeSet normal   = ctx.addAttribute(ctx.getEmptySet(), StyleConstants.Foreground, normalColor);
        AttributeSet kwd      = ctx.addAttribute(ctx.getEmptySet(), StyleConstants.Foreground, kwdColor);
        AttributeSet typ      = ctx.addAttribute(ctx.getEmptySet(), StyleConstants.Foreground, typColor);
        AttributeSet castAttr = ctx.addAttribute(ctx.getEmptySet(), StyleConstants.Foreground, castColor);
        AttributeSet num      = ctx.addAttribute(ctx.getEmptySet(), StyleConstants.Foreground, numColor);
        AttributeSet cmt      = ctx.addAttribute(ctx.getEmptySet(), StyleConstants.Foreground, cmtColor);
        AttributeSet preproc  = ctx.addAttribute(ctx.getEmptySet(), StyleConstants.Foreground, prepColor);

        doc.setCharacterAttributes(0, text.length(), normal, true);

        highlightPattern(doc, text, "(?m)^#\\s*\\w+", preproc);
        highlightPattern(doc, text,
            "\\b(void|bool|float|double|auto|" +
            "int8_t|int16_t|int32_t|int64_t|" +
            "uint8_t|uint16_t|uint32_t|uint64_t|" +
            "size_t|ptrdiff_t|intptr_t|uintptr_t|" +
            "char|short|long|signed|unsigned)\\b", typ);
        highlightPattern(doc, text, "\\bint[1-9]\\b", typ);
        highlightPattern(doc, text,
            "\\b(DWORD|WORD|BYTE|BOOL|HANDLE|HWND|HMODULE|HRESULT|" +
            "LPVOID|LPCSTR|LPSTR|LPCWSTR|LPWSTR|LPDWORD|" +
            "LARGE_INTEGER|ULARGE_INTEGER|_FILETIME|FILETIME|SYSTEMTIME|_SYSTEMTIME|" +
            "__uint64|__int64|ULONGLONG|LONGLONG|INT|UINT|ULONG|LONG|" +
            "wchar_t|PVOID|PCHAR)\\b", typ);
        highlightPattern(doc, text, "'(?:\\\\.|[^\\\\'])'" , num, 0);
        highlightPattern(doc, text,
            "\\b(static_cast|reinterpret_cast|const_cast|dynamic_cast)\\b", castAttr, 0);
        highlightPattern(doc, text,
            "\\b(if|else|return|while|do|for|switch|case|break|continue|goto|default|" +
            "nullptr|NULL|true|false|const|static|inline|extern|volatile|" +
            "struct|class|union|enum|typedef|sizeof|__cdecl|__stdcall|__fastcall)\\b", kwd, 0);
        highlightPattern(doc, text, "\\b(0x[0-9a-fA-F]+[UuLl]*|\\d+\\.?\\d*[fFuUlL]*)\\b", num, 0);
        highlightPattern(doc, text,
            "\\b(?!if|else|for|while|do|switch|return|sizeof)([a-zA-Z_][a-zA-Z0-9_]*)(?=\\s*\\()", castAttr, 0);
        highlightPattern(doc, text, "//.*",          cmt, Pattern.MULTILINE);
        highlightPattern(doc, text, "/\\*.*?\\*/",   cmt, Pattern.DOTALL);
    }

    private void highlightPattern(StyledDocument doc, String text, String pattern, AttributeSet attr) {
        highlightPattern(doc, text, pattern, attr, 0);
    }

    private void highlightPattern(StyledDocument doc, String text, String pattern, AttributeSet attr, int flags) {
        Matcher m = Pattern.compile(pattern, flags).matcher(text);
        while (m.find()) {
            int start = m.start(m.groupCount() > 0 ? 1 : 0);
            int end   = m.end(m.groupCount() > 0 ? 1 : 0);
            doc.setCharacterAttributes(start, end - start, attr, false);
        }
    }

    private void copyToClipboard() {
        String text = outputArea.getText();
        if (text != null && !text.isBlank()) {
            Toolkit.getDefaultToolkit()
                   .getSystemClipboard()
                   .setContents(new StringSelection(text), null);
            statusLabel.setText("Copied to clipboard!");
        }
    }
}
