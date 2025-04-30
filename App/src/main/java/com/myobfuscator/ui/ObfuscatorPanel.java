package com.myobfuscator.ui;

import com.myobfuscator.core.*;
import com.myobfuscator.transformer.NoOpTransformer;
import com.myobfuscator.transformer.RenamerTransformer;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ObfuscatorPanel extends JPanel {
    private final JTextField inputField = new JTextField(30);
    private final JTextField outputField = new JTextField(30);
    private final JCheckBox renamerCB = new JCheckBox("Rename");
    private final JCheckBox stringsCB = new JCheckBox("Encrypt Strings");
    private final JCheckBox cfCB      = new JCheckBox("Control-Flow");
    private final JCheckBox antiCB    = new JCheckBox("Anti-Debug");
    private final JButton runButton   = new JButton("Запустить");

    public ObfuscatorPanel() {
        add(new JLabel("Input JAR:"));  add(inputField);
        add(new JLabel("Output JAR:")); add(outputField);
        add(renamerCB);   add(stringsCB);
        add(cfCB);        add(antiCB);
        add(runButton);

        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Path input  = Paths.get(inputField.getText());
                Path output = Paths.get(outputField.getText());

                var transformers = new java.util.ArrayList<ITransformer>();
                //if (renamerCB.isSelected()) transformers.add(new NoOpTransformer());
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
    }
}

