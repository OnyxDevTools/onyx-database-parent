/*
 * Onyx Persistence API
 * Access your database via Web Services
 *
 * OpenAPI spec version: 2.0.0
 * 
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


package io.swagger.client.model;

import java.util.Objects;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.IOException;

/**
 * OnyxException
 */
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaClientCodegen", date = "2017-12-27T19:03:42.962Z")
public class OnyxException {
  @SerializedName("message")
  private String message = null;

  @SerializedName("cause")
  private String cause = null;

  @SerializedName("stack")
  private String stack = null;

  public OnyxException message(String message) {
    this.message = message;
    return this;
  }

   /**
   * Error message
   * @return message
  **/
  @ApiModelProperty(value = "Error message")
  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public OnyxException cause(String cause) {
    this.cause = cause;
    return this;
  }

   /**
   * Error cause
   * @return cause
  **/
  @ApiModelProperty(value = "Error cause")
  public String getCause() {
    return cause;
  }

  public void setCause(String cause) {
    this.cause = cause;
  }

  public OnyxException stack(String stack) {
    this.stack = stack;
    return this;
  }

   /**
   * Error stack trace
   * @return stack
  **/
  @ApiModelProperty(value = "Error stack trace")
  public String getStack() {
    return stack;
  }

  public void setStack(String stack) {
    this.stack = stack;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    OnyxException onyxException = (OnyxException) o;
    return Objects.equals(this.message, onyxException.message) &&
        Objects.equals(this.cause, onyxException.cause) &&
        Objects.equals(this.stack, onyxException.stack);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, cause, stack);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class OnyxException {\n");
    
    sb.append("    message: ").append(toIndentedString(message)).append("\n");
    sb.append("    cause: ").append(toIndentedString(cause)).append("\n");
    sb.append("    stack: ").append(toIndentedString(stack)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
  
}

