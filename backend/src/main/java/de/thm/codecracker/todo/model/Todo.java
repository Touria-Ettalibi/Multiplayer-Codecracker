package de.thm.codecracker.todo.model;

/**
 * Represents a Todo in the application domain.
 *
 * @param id        the unique Todo ID
 * @param text      the Todo text
 * @param done      whether the Todo is completed
 * @param createdAt the creation time as Unix epoch milliseconds
 * @param updatedAt the last update time as Unix epoch milliseconds
 */
public record Todo(
  Long id,
  String text,
  boolean done,
  long createdAt,
  long updatedAt
) {
}
