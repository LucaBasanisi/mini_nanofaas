package com.example.mini_nanofaas;

public interface ProviderFunzione {
    boolean deploy(FunctionSpec function);

    boolean remove(String functionName);

    boolean scale(String functionName, int replicas);
}
