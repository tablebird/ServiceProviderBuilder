package com.tablebird.serviceproviderbuilder;

import androidx.annotation.NonNull;

/**
 * An service achieve builder
 */
public interface ServiceBuilder<S> {
    @NonNull
    S load();
}
