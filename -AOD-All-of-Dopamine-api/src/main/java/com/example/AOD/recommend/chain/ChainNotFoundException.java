package com.example.AOD.recommend.chain;

import java.util.UUID;

/** 체인이 없거나 만료됐거나 남의 것이거나 다른 탭이다 → 404 (프론트는 chainId 없이 다시 요청한다). */
public class ChainNotFoundException extends RuntimeException {

    public ChainNotFoundException(UUID chainId) {
        super("Chain not found: " + chainId);
    }
}
