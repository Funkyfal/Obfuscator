package com.myobfuscator.ui;

import javax.swing.*;

public class GuiLauncher {
    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("My Obfuscator");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.getContentPane().add(new ObfuscatorPanel());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
