package com.jcash.app.transfers;

public class SelfTransferNotAllowedException extends RuntimeException {

    public SelfTransferNotAllowedException() {
        super("Self-transfer is not allowed");
    }
}
