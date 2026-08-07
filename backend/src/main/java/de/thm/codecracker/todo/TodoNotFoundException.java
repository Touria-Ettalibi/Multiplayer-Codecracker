package de.thm.codecracker.todo;

/**
 * Indicates that a requested Todo does not exist.
 */
public class TodoNotFoundException extends RuntimeException {
  public TodoNotFoundException() {
    super("Todo not found");
  }
}
