package com.sccadd.iface;

/**
 * A filter that can hold more than SCC's nine entries (the Storage Connector's holds 63, seven pages of nine,
 * like an AE2 Storage Bus with every capacity card) and, on the client, shows one page at a time: the screen
 * keeps working with slots 0 to 8 while this shifts them to the page being looked at.
 */
public interface FilterWindow {
    int SCC_PAGE = 9;
    int BUS_SIZE = 63;
    /** Set while a big filter is being constructed: the array is made before any field can say how big. */
    ThreadLocal<Boolean> BIG = ThreadLocal.withInitial(() -> false);

    int sccadd$capacity();

    int sccadd$base();

    void sccadd$setBase(int base);
}
