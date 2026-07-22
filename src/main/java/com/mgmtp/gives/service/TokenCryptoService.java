package com.mgmtp.gives.service;

public interface TokenCryptoService {
    String encrypt(String plainText);

    String decrypt(String encryptedText);
}
