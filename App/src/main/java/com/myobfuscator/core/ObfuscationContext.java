package com.myobfuscator.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

public class ObfuscationContext {
    private final Path inputJar;
    private final Path outputJar;
    private final List<ITransformer> transformers;

    private final int deadBranchCount;
    private final Random random = new Random(/*seed из GUI*/);

    public final Random getRandom() { return random; }

    public ObfuscationContext(Path inputJar, Path outputJar, List<ITransformer> transformers, int deadBranchCount) {
        this.inputJar = inputJar;
        this.outputJar = outputJar;
        this.transformers = transformers;
        this.deadBranchCount = deadBranchCount;
    }

    public Path getInputJar() { return inputJar; }
    public Path getOutputJar() { return outputJar; }
    public List<ITransformer> getTransformers() { return transformers; }
    public int getDeadBranchCount() {
        return deadBranchCount;
    }
}