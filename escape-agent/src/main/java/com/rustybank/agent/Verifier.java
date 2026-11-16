package com.rustybank.agent;

/** Judges a workspace. The real one runs Maven; tests use a fake. */
public interface Verifier {

    VerifyResult verify(Workspace workspace);
}
