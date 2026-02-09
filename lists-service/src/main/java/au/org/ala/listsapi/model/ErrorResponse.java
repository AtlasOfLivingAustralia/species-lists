package au.org.ala.listsapi.model;

import lombok.NoArgsConstructor;

/** Represents an error response for API calls. */
@NoArgsConstructor
public class ErrorResponse {
  // Error response fields, e.g.NotFound, BadRequest, etc.
  private String error;
  // A human-readable message describing the error
  private String message;
  // The HTTP status code associated with the error
  private int status;

  public ErrorResponse(String error, String message, int status) {
    this.error = error;
    this.message = message;
    this.status = status;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String error;
    private String message;
    private int status;

    public Builder error(String error) {
      this.error = error;
      return this;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public Builder status(int status) {
      this.status = status;
      return this;
    }

    public ErrorResponse build() {
      return new ErrorResponse(error, message, status);
    }
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }
}
