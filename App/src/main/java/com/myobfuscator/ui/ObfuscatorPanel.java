package com.myobfuscator.ui;

import com.myobfuscator.core.*;
import com.myobfuscator.transformer.RenamerTransformer;
import org.objectweb.asm.ClassReader;

import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.Textifier;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ObfuscatorPanel extends JPanel {
    private final JTextField inputField =
            new JTextField("app/test-src/test-jars/HelloWorld.jar",30);
    private final JTextField outputField =
            new JTextField("app/test-src/test-jars/HelloWorld-obfus.jar",30);
    private final JCheckBox renamerCB = new JCheckBox("Rename");
    private final JCheckBox stringsCB = new JCheckBox("Encrypt Strings");
    private final JCheckBox cfCB      = new JCheckBox("Control-Flow");
    private final JCheckBox antiCB    = new JCheckBox("Anti-Debug");
    private final JButton runButton   = new JButton("Запустить");
    private final JButton disasmButton = new JButton("Disassemble JAR");

    public ObfuscatorPanel() {
        setPreferredSize(new Dimension(700, 200));
        add(new JLabel("Input JAR:"));  add(inputField);
        add(new JLabel("Output JAR:")); add(outputField);
        add(renamerCB);
        add(stringsCB);
        add(cfCB);
        add(antiCB);
        add(runButton);
        add(disasmButton);

        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Path input  = Paths.get(inputField.getText());
                Path output = Paths.get(outputField.getText());

                var transformers = new java.util.ArrayList<ITransformer>();
//                if (renamerCB.isSelected()) transformers.add(new NoOpTransformer());
                if (renamerCB.isSelected()) transformers.add(new RenamerTransformer());
//                if (stringsCB.isSelected())  transformers.add(new StringEncryptorTransformer());
//                if (cfCB.isSelected())       transformers.add(new ControlFlowTransformer());
//                if (antiCB.isSelected())     transformers.add(new AntiDebugTransformer());

                ObfuscationContext ctx = new ObfuscationContext(input, output, transformers);

                // Запуск в фоне, чтобы не блокировать GUI
                new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() {
                        try {
                            System.out.println("→ Input JAR = " + input.toAbsolutePath());
                            System.out.println("→ Exists?   = " + Files.exists(input));
                            new ObfuscatorCore(ctx).run();
                        } catch (Exception ex) {
                            ex.printStackTrace();  // выведет стек-трейс в консоль
                            SwingUtilities.invokeLater(() ->
                                    JOptionPane.showMessageDialog(
                                            ObfuscatorPanel.this,
                                            "Ошибка: " + ex.getMessage(),
                                            "Ошибка",
                                            JOptionPane.ERROR_MESSAGE
                                    ));
                        }
                        return null;
                    }
                    @Override
                    protected void done() {
                        JOptionPane.showMessageDialog(
                                ObfuscatorPanel.this,
                                "Обфускация завершена: " + output,
                                "Готово",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    }
                }.execute();
            }
        });

        disasmButton.addActionListener(e -> {
            // 1) Выбор JAR
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JAR Files", "jar"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File jarFile = chooser.getSelectedFile();

            try (JarFile jar = new JarFile(jarFile)) {
                var classEntries = new ArrayList<String>();
                Enumeration<JarEntry> ents = jar.entries();
                while (ents.hasMoreElements()) {
                    JarEntry je = ents.nextElement();
                    if (je.getName().endsWith(".class")) {
                        classEntries.add(je.getName());
                    }
                }
                if (classEntries.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "В JAR нет .class-файлов",
                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 2) Предлагаем выбрать
                String selected = (String) JOptionPane.showInputDialog(
                        this,
                        "Выберите класс для дизассемблера:",
                        "Select Class",
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        classEntries.toArray(new String[0]),
                        classEntries.get(0)
                );
                if (selected == null) return; // отмена

                // 3) Дизассемблируем выбранный
                try (InputStream is = jar.getInputStream(jar.getJarEntry(selected))) {
                    ClassReader cr = new ClassReader(is);
                    StringWriter sw = new StringWriter();
                    TraceClassVisitor tcv = new TraceClassVisitor(
                            null, new Textifier(), new PrintWriter(sw)
                    );
                    cr.accept(tcv, ClassReader.SKIP_FRAMES);
                    String disasm = sw.toString();

                    // 4) Показать в диалоге
                    JTextArea area = new JTextArea(disasm);
                    area.setEditable(false);
                    JDialog dialog = new JDialog(
                            SwingUtilities.getWindowAncestor(this),
                            "Disassembly: " + selected
                    );
                    dialog.getContentPane().add(new JScrollPane(area));
                    dialog.setSize(800, 600);
                    dialog.setLocationRelativeTo(this);
                    dialog.setModal(true);
                    dialog.setVisible(true);
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Ошибка: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}

