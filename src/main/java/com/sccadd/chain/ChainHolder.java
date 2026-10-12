package com.sccadd.chain;

import org.jetbrains.annotations.Nullable;

/** Implemented onto the requester block entity: the chain of each of its rows. */
public interface ChainHolder {
    @Nullable
    ChainData sccadd$chain(int row);

    void sccadd$setChain(int row, @Nullable ChainData chain);
}
