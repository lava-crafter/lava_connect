// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect;

interface IShizukuGrantService {
    // Shizuku calls this transaction when unbinding with remove = true.
    void destroy() = 16777114;

    // Returns null on success, otherwise the failed commands and their output.
    String grant(String packageName) = 1;
}
