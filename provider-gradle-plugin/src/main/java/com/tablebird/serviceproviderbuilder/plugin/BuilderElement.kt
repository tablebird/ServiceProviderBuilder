package com.tablebird.serviceproviderbuilder.plugin

/**
 *
 * @author tablebird
 * @date 2019/11/8
 */
class BuilderElement constructor() {
    var key: String? = null
    var isSingle: Boolean = false
    var values: LinkedHashSet<String>? = null

    @JvmOverloads
    constructor(key: String, isSingle: Boolean = false) : this() {
        this.key = key
        this.isSingle = isSingle
    }
}