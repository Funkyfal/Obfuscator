package com.myobfuscator;

import com.myobfuscator.security.SystemBindingUtil;
import com.myobfuscator.ui.GuiLauncher;

public class Main {
    public static void main(String[] args) {
        System.out.println("CURRENT_HASH=" + SystemBindingUtil.computeSystemHash());
        GuiLauncher.launch();
    }
}
