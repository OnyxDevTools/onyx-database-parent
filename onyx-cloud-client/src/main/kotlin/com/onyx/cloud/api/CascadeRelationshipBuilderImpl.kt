package com.onyx.cloud.api

/**
 * Default implementation of [ICascadeRelationshipBuilder] for building cascade metadata strings.
 */
internal class CascadeRelationshipBuilderImpl : ICascadeRelationshipBuilder {
    private var graphName: String? = null
    private var graphType: String? = null
    private var target: String? = null

    /**
     * Names the relationship graph.
     *
     * @param name Relationship graph name.
     * @return This builder for chaining.
     */
    override fun graph(name: String): ICascadeRelationshipBuilder {
        graphName = name
        return this
    }

    /**
     * Sets the graph type.
     *
     * @param type Graph type name.
     * @return This builder for chaining.
     */
    override fun graphType(type: String): ICascadeRelationshipBuilder {
        graphType = type
        return this
    }

    /**
     * Field on the target entity.
     *
     * @param field Target field name.
     * @return This builder for chaining.
     */
    override fun targetField(field: String): ICascadeRelationshipBuilder {
        target = field
        return this
    }

    /**
     * Field on the source entity and completes the builder chain.
     *
     * @param field Source field name.
     * @return Built cascade relationship string in the form "graphName:graphType(sourceField,targetField)".
     */
    override fun sourceField(field: String): String {
        val name = graphName ?: error("Graph name must be specified")
        val type = graphType ?: error("Graph type must be specified")
        val tgt = target ?: error("Target field must be specified")
        return "$name:$type($tgt,$field)"
    }
}
