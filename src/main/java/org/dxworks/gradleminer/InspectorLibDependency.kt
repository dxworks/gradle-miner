package org.dxworks.gradleminer

data class InspectorLibDependency(
        val name: String,
        val version: String?,
        val provider: String = "gradle"
)
