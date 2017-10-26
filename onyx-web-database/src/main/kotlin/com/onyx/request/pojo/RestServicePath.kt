package com.onyx.request.pojo

/**
 * Created by tosborn on 12/31/15.
 * This contains the list of services for the restful web service api within Onyx
 */
enum class RestServicePath constructor(val path: String) {

    SAVE("/saveEntity"),
    FIND_BY_PARTITION_REFERENCE("/findByPartitionReference"),
    FIND_BY_PARTITION("/findWithPartitionId"),
    FIND("/find"),
    DELETE("/deleteEntity"),
    EXECUTE("/execute"),
    INITIALIZE("/initialize"),
    BATCH_SAVE("/batchSave"),
    BATCH_DELETE("/batchDelete"),
    EXECUTE_UPDATE("/executeUpdate"),
    EXECUTE_DELETE("/executeDelete"),
    EXISTS("/exists"),
    QUERY_COUNT("/queryCount"),
    SAVE_RELATIONSHIPS("/saveRelationships");

    companion object {

        fun valueOfPath(name: String): RestServicePath {

            RestServicePath.values()
                    .asSequence()
                    .filter { it.path.equals(name, ignoreCase = true) }
                    .forEach { return it }

            throw IllegalArgumentException(String.format(
                    "There is no key with name '%s' in Enum RestServicePath",
                    name))
        }
    }
}
