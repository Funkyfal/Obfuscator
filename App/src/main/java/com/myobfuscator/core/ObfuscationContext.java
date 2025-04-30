package com.myobfuscator.core;

import java.nio.file.Path;
import java.util.List;

public class ObfuscationContext {
    private final Path inputJar;
    private final Path outputJar;
    private final List<ITransformer> transformers;

    public ObfuscationContext(Path inputJar, Path outputJar, List<ITransformer> transformers) {
        this.inputJar = inputJar;
        this.outputJar = outputJar;
        this.transformers = transformers;
    }

    public Path getInputJar() { return inputJar; }
    public Path getOutputJar() { return outputJar; }
    public List<ITransformer> getTransformers() { return transformers; }
}