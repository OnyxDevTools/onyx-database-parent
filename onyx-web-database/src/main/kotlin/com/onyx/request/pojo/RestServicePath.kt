package com.onyx.request.pojo

/**
 * Created by Tim Osborn on 12/31/15.
 * This contains the list of services for the restful web service api within Onyx
 */
enum class RestServicePath constructor(val path: String) {

    SAVE("/saveEntity"),
    FIND_BY_ID("/findById"),
    DELETE("/deleteEntity"),
    INITIALIZE("/initialize"),
    BATCH_SAVE("/saveEntities"),
    BATCH_DELETE("/deleteEntities"),
    EXECUTE("/executeQuery"),
    EXECUTE_UPDATE("/executeUpdate"),
    EXECUTE_DELETE("/executeDelete"),
    EXISTS_WITH_ID("/existsWithId"),
    QUERY_COUNT("/count"),
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
